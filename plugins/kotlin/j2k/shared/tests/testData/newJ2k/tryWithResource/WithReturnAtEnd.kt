import java.io.ByteArrayInputStream
import java.io.IOException

class C {
    fun foo(): Int {
        try {
            ByteArrayInputStream(ByteArray(10)).use { stream ->
                // reading something
                val c = stream.read()
                return c
            }
        } catch (e: IOException) {
            println(e)
            return -1
        }
    }
}
