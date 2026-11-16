// ERROR: UNRESOLVED_REFERENCE
internal class A {
    fun changed(list: MutableList<String>) {
        list.reversed().add("a")
    }

    fun read(list: List<String?>): String? {
        return list.reversed().get(0)
    }
}
