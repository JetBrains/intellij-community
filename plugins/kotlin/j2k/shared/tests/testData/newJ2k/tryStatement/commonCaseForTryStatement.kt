import java.io.IOException

internal class C {
    fun foo() {
        try {
            println()
        } catch (e: IOException) {
            println(1)
        } catch (e: Exception) {
            println(0)
        } finally {
            println(3)
        }
    }
}
