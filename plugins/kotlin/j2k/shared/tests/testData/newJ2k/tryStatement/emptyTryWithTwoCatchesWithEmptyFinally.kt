import java.io.IOException

internal class C {
    fun foo() {
        try {
        } catch (e: Exception) {
            println(1)
        } catch (e: IOException) {
            println(0)
        } finally {
        }
    }
}
