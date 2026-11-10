package helium314.keyboard.qatt.compose

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The downloader against a server of its own (the network of the keyboard is never used in a test). */
class FontDownloaderTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val requests = ArrayList<Map<String, String>>()
    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"
    private val anyLocal = FontDownloader { it.host == "127.0.0.1" }

    private val font = ByteArray(1_200_000) { (it * 31).toByte() }.also { it[0] = 0; it[1] = 1; it[2] = 0; it[3] = 0 }   // TrueType header
    private val etag = "\"abc123\""

    @BeforeTest fun start() {
        dir = kotlin.io.path.createTempDirectory("fontdl").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { x: HttpExchange ->
            requests.add(mapOf("path" to x.requestURI.path, "ua" to (x.requestHeaders.getFirst("User-Agent") ?: ""), "inm" to (x.requestHeaders.getFirst("If-None-Match") ?: "")))
            when (x.requestURI.path) {
                "/font.ttf" -> if (x.requestHeaders.getFirst("If-None-Match") == etag) x.sendResponseHeaders(304, -1)
                    else { x.responseHeaders.add("ETag", etag); x.sendResponseHeaders(200, font.size.toLong()); x.responseBody.use { it.write(font) } }
                "/small.ttf" -> { val b = ByteArray(1000); x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) } }
                "/page.html" -> { val b = ByteArray(2_000_000) { 'x'.code.toByte() }; x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) } }
                "/cut.ttf" -> { x.sendResponseHeaders(200, font.size.toLong()); x.responseBody.use { it.write(font, 0, 500_000) } }    // the connection ends early
                "/moved.ttf" -> { x.responseHeaders.add("Location", "http://evil.example/font.ttf"); x.sendResponseHeaders(302, -1) }
                else -> x.sendResponseHeaders(404, -1)
            }
            x.close()
        }
        server.start()
    }

    @AfterTest fun stop() { server.stop(0); dir.deleteRecursively() }

    @Test fun downloads() {
        val target = File(dir, "fonts/qatt-pen.ttf")
        val progress = ArrayList<Pair<Long, Long>>()
        val r = anyLocal.download(url("/font.ttf"), target, null, { done, total -> progress.add(done to total) })
        assertEquals(FontDownloader.Result.Downloaded(etag, font.size.toLong()), r)
        assertTrue(target.readBytes().contentEquals(font))
        assertEquals(font.size.toLong() to font.size.toLong(), progress.last())
        assertTrue(progress.size > 2)                                          // the progress is reported on the way
        assertEquals(listOf("qatt-pen.ttf"), target.parentFile.list()!!.toList())   // no part file is left
        assertEquals("QATT-Keyboard", requests.single()["ua"])                 // nothing else about the user is sent
        assertEquals("", requests.single()["inm"])
    }

    @Test fun unchangedVersionIsNotDownloadedAgain() {
        val target = File(dir, "qatt-pen.ttf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(FontDownloader.Result.Current, anyLocal.download(url("/font.ttf"), target, etag))
        assertEquals(etag, requests.single()["inm"])
        assertEquals(listOf<Byte>(1, 2, 3), target.readBytes().toList())      // untouched
        // another version is taken
        assertTrue(anyLocal.download(url("/font.ttf"), target, "\"old\"") is FontDownloader.Result.Downloaded)
        assertEquals(font.size, target.length().toInt())
    }

    @Test fun badFilesAreNotTaken() {
        for (path in listOf("/small.ttf", "/page.html", "/cut.ttf", "/moved.ttf", "/missing.ttf")) {
            val target = File(dir, "x" + path.replace('/', '_')).apply { writeBytes(byteArrayOf(9)) }
            val r = anyLocal.download(url(path), target)
            assertTrue(r is FontDownloader.Result.Failed, "$path: $r")
            assertEquals(listOf<Byte>(9), target.readBytes().toList(), path)    // the file that was there stays
        }
        assertEquals(emptyList(), dir.list()!!.filter { it.endsWith(".part") })
    }

    @Test fun aRedirectIsNotFollowed() {
        val r = anyLocal.download(url("/moved.ttf"), File(dir, "a.ttf"))
        assertEquals(FontDownloader.Result.Failed("HTTP 302"), r)
        assertEquals(1, requests.size)                                          // only our server was asked
    }

    @Test fun cancelling() {
        val target = File(dir, "c.ttf")
        val r = anyLocal.download(url("/font.ttf"), target, null, cancelled = { true })
        assertEquals(FontDownloader.Result.Failed("cancelled"), r)
        assertFalse(target.exists())
        assertEquals(emptyList(), dir.list()!!.toList())
    }

    @Test fun onlyTheFontServerIsAllowed() {
        assertTrue(FontDownloader.isAllowed(URI("https://cdn.qatt.org/fonts/qatt-pen.ttf").toURL()))
        assertFalse(FontDownloader.isAllowed(URI("http://cdn.qatt.org/fonts/qatt-pen.ttf").toURL()))           // not plain HTTP
        assertFalse(FontDownloader.isAllowed(URI("https://cdn.qatt.org.evil.example/f.ttf").toURL()))
        assertFalse(FontDownloader.isAllowed(URI("https://evil.example/cdn.qatt.org").toURL()))
        assertFalse(FontDownloader.isAllowed(URI("https://cdn.qatt.org@evil.example/f.ttf").toURL()))
        assertFalse(FontDownloader.isAllowed(URI("https://user:pw@cdn.qatt.org/f.ttf").toURL()))
        assertFalse(FontDownloader.isAllowed(URI("https://cdn.qatt.org:8443/f.ttf").toURL()))
        // the default refuses a local address, whatever is asked
        assertEquals(FontDownloader.Result.Failed("address not allowed"), FontDownloader().download(url("/font.ttf"), File(dir, "n.ttf")))
        assertTrue(requests.isEmpty())
        assertEquals("https://cdn.qatt.org/fonts/migoi.ttf", FontDownloader.urlOf(ComposeFont.MIGOI))
    }
}
