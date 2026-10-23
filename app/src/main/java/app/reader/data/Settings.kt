package app.reader.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.reader.theme.HapticLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** System follows the device's dark setting; at night it uses [AppSettings.night]. */
enum class ThemeChoice { System, Light, Dark, Black }
enum class NightChoice { Dark, Black }
enum class Margins(val readium: Double) { Narrow(0.6), Normal(1.0), Wide(1.6) }
enum class Spread { Auto, One, Two }
enum class PageAnim { Instant, Slide }
enum class TapSide { Right, Left }
enum class Sort { Recent, Title, Author }

/**
 * Per-device settings. They don't sync: each device keeps its own (a phone and a tablet
 * want different sizes). Reading positions sync through the book folder instead, see [Sidecars].
 */
@Serializable
data class AppSettings(
    val look: String = "readout",
    /** null until the user picks one: each look then uses its own default (Plotter is light-first). */
    val theme: ThemeChoice? = null,
    val night: NightChoice = NightChoice.Black,
    val warmth: Int = 0,
    /** The saved reading face. A look that doesn't offer it shows its own default without overwriting this. */
    val font: String? = null,
    val size: Int = 18,
    val leading: Float = 1.6f,
    val margins: Margins = Margins.Normal,
    val justify: Boolean = false,
    val hyphens: Boolean = true,
    val spread: Spread = Spread.Auto,
    val anim: PageAnim = PageAnim.Instant,
    val tapSide: TapSide = TapSide.Right,
    val volumeKeys: Boolean = true,
    val pdfRecolor: Boolean = true,
    val pdfTrim: Boolean = true,
    val haptics: HapticLevel = HapticLevel.Quiet,
    val pageTick: Boolean = false,
    val sort: Sort = Sort.Recent,
    val folder: String? = null,
    val deviceId: String = "",
)

private val Context.store by preferencesDataStore("settings")
private val KEY = stringPreferencesKey("settings.json")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private val initial: AppSettings = runBlocking { read() }

    val settings: StateFlow<AppSettings> = context.store.data
        .map { p -> p[KEY]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: initial }
        .stateIn(scope, SharingStarted.Eagerly, initial)

    val current get() = settings.value

    private val scope = scope

    fun update(f: (AppSettings) -> AppSettings) {
        scope.launch { context.store.edit { p -> p[KEY] = json.encodeToString(f(decode(p[KEY]))) } }
    }

    private fun decode(s: String?) = s?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: initial

    private suspend fun read(): AppSettings {
        val stored = decode(context.store.data.first()[KEY])
        if (stored.deviceId.isNotEmpty()) return stored
        // A stable id per install, readable in the sidecar file names.
        val slug = Build.MODEL.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "device" }
        val withId = stored.copy(deviceId = "$slug-${Random.nextInt(0x1000, 0xFFFF).toString(16)}")
        context.store.edit { it[KEY] = json.encodeToString(withId) }
        return withId
    }
}
