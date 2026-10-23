package app.reader.reader

import android.content.Context
import android.net.Uri
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** Opens EPUBs (and our converted TXT/MD) with Readium. PDFs use the platform renderer instead. */
class Readium(context: Context) {
    private val http = DefaultHttpClient()
    private val assets = AssetRetriever(context.contentResolver, http)
    private val opener = PublicationOpener(DefaultPublicationParser(context, http, assets, pdfFactory = null))

    suspend fun retrieve(uri: Uri): Publication {
        val url = uri.toAbsoluteUrl() ?: error("Unsupported location.")
        val asset = assets.retrieve(url).getOrElse { error("Can't read this file: ${it.message}") }
        return opener.open(asset, allowUserInteraction = false).getOrElse { error("Not a readable EPUB: ${it.message}") }
    }

    suspend fun retrieveFile(file: File): Publication {
        val asset = assets.retrieve(file).getOrElse { error("Can't read the converted text: ${it.message}") }
        return opener.open(asset, allowUserInteraction = false).getOrElse { error("Couldn't lay out this text: ${it.message}") }
    }
}
