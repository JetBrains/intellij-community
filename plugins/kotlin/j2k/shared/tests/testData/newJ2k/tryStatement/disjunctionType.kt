import java.io.IOException

internal class A {
    fun foo() {
        try {
            bar()
        } catch (e: RuntimeException) {
            e.printStackTrace() // print stack trace
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun bar() {
        throw IOException()
    }
}
