package demo

internal class Test {
    fun main() {
        val common: MutableList<String?> = ArrayList<String?>()
        val raw: MutableList<*> = ArrayList<String?>()
        val superRaw: MutableList<*> = ArrayList<Any?>()
    }
}
