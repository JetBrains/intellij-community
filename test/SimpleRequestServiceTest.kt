import com.intellij.stats.completion.SimpleRequestService
import org.junit.Test
import java.io.File

class SimpleRequestServiceTest {
    
    @Test
    fun `test huge load`() {
        val file = File("data.txt")
        val service = SimpleRequestService()
        
        val start = System.currentTimeMillis()
        service.post("http://localhost:8080/stats/upload/123", file)
        val end = System.currentTimeMillis()

        println(end - start)
    }
    
}