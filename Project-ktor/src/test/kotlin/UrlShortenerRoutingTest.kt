package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlShortenerRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testShortenAndRedirectAndStats() = testApplication {
        application {
            configureRouting()
        }

        //POST /shorten - ส่ง URL ยาว รับ โค้ดสั้น
        val longUrl = "https://www.example.com/test-page"
        val responseShorten = client.post("/shorten") {
            contentType(ContentType.Application.Json)
            setBody("""{"longUrl":"$longUrl"}""")
        }

        assertEquals(HttpStatusCode.OK, responseShorten.status)

        val shortenResponseBody = responseShorten.bodyAsText()
        val shortenResponse = json.decodeFromString(ShortenResponse.serializer(), shortenResponseBody)
        assertTrue(shortenResponse.shortUrl.startsWith("http://localhost:8080/"))

        // ดึง โค้ดสั้น จาก URL ที่ได้ (หลัง localhost:8080/)
        val shortCode = shortenResponse.shortUrl.removePrefix("http://localhost:8080/")


        //ทดสอบ Redirect ไป longUrl โดยไม่ตาม ที่อยู่
        val clientNoRedirect = createClient {
            followRedirects = false
        }

        val responseRedirect = clientNoRedirect.get("/$shortCode")
        assertTrue(responseRedirect.status == HttpStatusCode.Found || responseRedirect.status == HttpStatusCode.MovedPermanently)
        assertEquals(longUrl, responseRedirect.headers[HttpHeaders.Location])


        //เช็คการคลิกเเละสถานะ
        val responseStats = client.get("/stats/$shortCode")
        assertEquals(HttpStatusCode.OK, responseStats.status)

        val statsResponseBody = responseStats.bodyAsText()
        val stats = json.decodeFromString(UrlStats.serializer(), statsResponseBody)
        assertEquals(longUrl, stats.longUrl)
        assertEquals(1, stats.clicks) //ต้องมี 1 คลิกจากด้านบน
    }

    @Test
    fun testShortenInvalidUrl() = testApplication {
        application {
            configureRouting()
        }

        val invalidUrl = "ftp://invalid-url.com"
        val response = client.post("/shorten") {
            contentType(ContentType.Application.Json)
            setBody("""{"longUrl":"$invalidUrl"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Invalid URL"))
    }

    @Test
    fun testNotFoundShortCode() = testApplication {
        application {
            configureRouting()
        }

        val responseRedirect = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, responseRedirect.status)
        assertEquals("Short URL not found", responseRedirect.bodyAsText())

        val responseStats = client.get("/stats/nonexistent")
        assertEquals(HttpStatusCode.NotFound, responseStats.status)
        assertEquals("Not found", responseStats.bodyAsText())
    }
}
