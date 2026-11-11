// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.qatt.compose

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Downloads a big font into a file. The only thing in the keyboard that uses the network, and only when the user asks for it in the settings of the
 * composition field. Only HTTPS and only [HOST] (cdn.qatt.org) are allowed, a redirect is not followed, and the request carries nothing but the address of the file
 * (and the ETag of the version the keyboard has already, so that the server can answer "unchanged" without sending the 27 MB again).
 * The file is written next to its target and replaces it only when it is complete and looks like a font. Pure Java, no Android.
 */
class FontDownloader(private val allowed: (URL) -> Boolean = ::isAllowed) {
    sealed class Result {
        /** The server has the version that is known already (answer 304). */
        object Current : Result()
        data class Downloaded(val etag: String?, val bytes: Long) : Result()
        data class Failed(val reason: String) : Result()
    }

    /** [onProgress] gets the bytes so far and the total (-1 if unknown); [cancelled] is asked while reading. */
    fun download(url: String, target: File, knownEtag: String? = null, onProgress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): Result {
        val address = try { URI(url).toURL() } catch (e: Exception) { return Result.Failed("bad address") }
        if (!allowed(address)) return Result.Failed("address not allowed")
        val part = File(target.parentFile, target.name + ".part")
        var connection: HttpURLConnection? = null
        try {
            target.parentFile?.mkdirs()
            connection = address.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (!knownEtag.isNullOrEmpty()) connection.setRequestProperty("If-None-Match", knownEtag)
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return Result.Current
                HttpURLConnection.HTTP_OK -> {}
                else -> return Result.Failed("HTTP $code")
            }
            val total = connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var last = 0L
                    while (true) {
                        if (cancelled()) { return Result.Failed("cancelled") }
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - last >= 256 * 1024) { onProgress(done, total); last = done }
                    }
                }
            }
            onProgress(done, total)
            if (total >= 0 && done != total) return Result.Failed("incomplete")
            val header = part.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            if (done < FontFile.MIN_SIZE || !FontFile.looksLikeFont(header)) return Result.Failed("not a font")
            target.delete()
            if (!part.renameTo(target)) return Result.Failed("cannot write")
            return Result.Downloaded(connection.getHeaderField("ETag"), done)
        } catch (e: IOException) {
            return Result.Failed(e.message ?: "network error")
        } catch (e: Exception) {
            return Result.Failed(e.message ?: "error")
        } finally {
            connection?.disconnect()
            part.delete()
        }
    }

    companion object {
        const val HOST = "cdn.qatt.org"
        const val BASE = "https://$HOST/fonts/"
        private const val USER_AGENT = "QATT-Keyboard"

        fun isAllowed(url: URL) = url.protocol == "https" && url.host == HOST && url.port == -1 && url.userInfo == null

        fun urlOf(font: ComposeFont) = BASE + font.downloadName
    }
}
