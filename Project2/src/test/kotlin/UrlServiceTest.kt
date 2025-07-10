import kotlin.test.*
import com.example.*

class UrlServiceTest {

    private lateinit var repo: UrlRepository
    private lateinit var service: UrlService

    @BeforeTest
    fun setup() {
        repo = UrlRepository()
        service = UrlService(repo)
    }

    @Test
    fun `getRedirectInfo should increase click count`() {
        val mapping = UrlMapping("abc123", "https://example.com")
        repo.save(mapping)

        val result = service.getRedirectInfo("abc123")

        assertNotNull(result)
        assertEquals("https://example.com", result.originalUrl)
        assertEquals(1, result.clickCount)

        // เรียกอีกครั้งเพื่อดูว่า count เพิ่ม
        service.getRedirectInfo("abc123")
        assertEquals(2, result.clickCount)
    }

    @Test
    fun `getRedirectInfo returns null for missing code`() {
        val result = service.getRedirectInfo("not-exist")
        assertNull(result)
    }
}
