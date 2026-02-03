internal class A {
    fun println(s: String?) {
        kotlin.io.println(s)
    }

    fun print(s: String?) {
        kotlin.io.print(s)
    }

    fun <T> emptyList() {
        val list = kotlin.collections.emptyList<String>()
    }

    fun <T> emptySet() {
        val set = kotlin.collections.emptySet<String>()
    }

    fun <K, V> emptyMap() {
        val map = kotlin.collections.emptyMap<String, String>()
    }

    fun <T> listOf(s: String) {
        val list = kotlin.collections.listOf(s)
    }

    fun <T> setOf(s: String) {
        val set = kotlin.collections.setOf(s)
    }
}
