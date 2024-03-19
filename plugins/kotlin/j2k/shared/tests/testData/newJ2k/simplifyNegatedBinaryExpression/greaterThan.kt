internal class C {
    fun foo(o: Any?) {
        if (0 >= 1) return
        println("Foo")
    }

    fun bar(o: Any?) {
        if (0 > 1) return
        println("Bar")
    }
}
