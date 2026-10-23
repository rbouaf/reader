package app.reader

import android.app.Application
import app.reader.data.LibraryRepository
import app.reader.data.SettingsRepository
import app.reader.reader.Readium
import app.reader.theme.Fonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReaderApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsRepository(this, scope) }
    val library by lazy { LibraryRepository(this, settings, scope) }
    val readium by lazy { Readium(this) }

    override fun onCreate() {
        super.onCreate()
        Fonts.init(assets)
    }
}

val android.content.Context.app get() = applicationContext as ReaderApp
