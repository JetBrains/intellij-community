internal class A {
    fun chained() {
        val a: MutableList<String>
        val b: MutableList<String>
        b = mutableListOf<String>("x", "y")
        a = b
        a.set(0, "z")
    }
}
