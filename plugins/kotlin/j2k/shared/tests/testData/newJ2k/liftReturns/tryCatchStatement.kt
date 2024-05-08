internal class Test {
    fun test(n: Int): String {
        return try {
            "success"
        } catch (e: Exception) {
            "failure"
        } finally {
            println("tried running script")
        }
    }
}
