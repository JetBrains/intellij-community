internal class A {
    fun println(s: String?) {
        kotlin.io.println(s)
    }

    fun print(s: String?) {
        kotlin.io.print(s)
    }

    fun <T> emptyList() {
        val list = mutableListOf<String?>()
    }

    fun <T> emptySet() {
        val set = mutableSetOf<String?>()
    }

    fun <K, V> emptyMap() {
        val map = mutableMapOf<String?, String?>()
    }

    fun <T> listOf(s: String?) {
        val list = mutableListOf<String?>(s)
    }

    fun <T> setOf(s: String?) {
        val set = mutableSetOf<String?>(s)
    }
}
