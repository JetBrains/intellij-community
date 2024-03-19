internal class C {
    fun foo(o: Any?) {
        if (0 == 1) return
        println("String")
    }

    fun bar(): Boolean {
        return 1 == 2 == (3 != 4)
    }
}
