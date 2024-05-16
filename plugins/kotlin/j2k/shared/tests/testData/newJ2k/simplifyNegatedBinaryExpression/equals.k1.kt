internal class C {
    fun foo(a: Int, b: Int, s1: String, s2: String) {
        if (0 == 1) return
        if (0 == 1 && a > b) return
        if (0 == 1 &&  /*comment 1*/ /*comment 2*/a != b) return

        if (s1 !== s2) return
        if (s1 === s2) return
    }

    fun bar(): Boolean {
        return 1 == 2 == (3 != 4)
    }
}
