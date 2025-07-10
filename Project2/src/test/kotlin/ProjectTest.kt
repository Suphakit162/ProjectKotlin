import com.example.*
import io.ktor.server.testing.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.client.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*

class ProjectTest {

    @Test
    fun `testCreateAndRedirect`() = testApplication {
        val repo = UrlRepository()

        application {
            install(ContentNegotiation) {
                json()
            }
            configureUrlShortenerRoutes(repo)
        }

        val client = createClient {
            install(HttpRedirect) {
                checkHttpMethod = false
                followRedirects = false
            }
        }

        repo.clear()

        val postResponse = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("""{"originalUrl":"/test-destination"}""")
        }

        assertEquals(HttpStatusCode.Created, postResponse.status)
        val shortUrl = postResponse.bodyAsText()
        val shortCode = shortUrl.substringAfterLast("/")

        val getResponse = client.get("/$shortCode")

        assertEquals(HttpStatusCode.Found, getResponse.status)
        assertEquals("/test-destination", getResponse.headers[HttpHeaders.Location])
    }

    @Test
    fun `testNotFoundShortCode`() = testApplication {
        val repo = UrlRepository()

        application {
            install(ContentNegotiation) {
                json()
            }
            configureUrlShortenerRoutes(repo)
        }

        val response = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Short URL not found", response.bodyAsText())
    }

    @Test
    fun `testClickCountIncrement`() = testApplication {
        val repo = UrlRepository()

        application {
            install(ContentNegotiation) {
                json()
            }
            configureUrlShortenerRoutes(repo)
        }

        val client = createClient {
            install(HttpRedirect) {
                followRedirects = false
            }
        }

        // Create short URL
        val postResponse = client.post("/") {
            contentType(ContentType.Application.Json)
            setBody("""{"originalUrl":"https://example.com"}""")
        }

        assertEquals(HttpStatusCode.Created, postResponse.status)
        val shortUrl = postResponse.bodyAsText()
        val shortCode = shortUrl.substringAfterLast("/")

        // Access the short URL multiple times
        repeat(3) {
            val getResponse = client.get("/$shortCode")
            assertEquals(HttpStatusCode.Found, getResponse.status)
        }

        // Verify click count in repository
        val mapping = repo.getByShortCode(shortCode)
        assertNotNull(mapping)
        assertEquals(3, mapping.clickCount)
    }
}