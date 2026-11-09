internal class A {
    fun readOnly() {
        val list = listOf<String>("a")
        val set = setOf<String>("a")
        val empty = emptyList<String?>()
        val asList = listOf<String>("a", "b")
    }

    fun changed() {
        val list = mutableListOf<String>("a", "b")
        list.set(0, "c")
    }

    fun notStored(): Int {
        return mutableListOf<String>("a").size
    }
}
