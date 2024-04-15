internal class A {
    fun foo(p: Boolean) {
        if (p) {
            val i = 10
        }

        run {
            var i = 1
            while (i < 1000) {
                println(i)
                i *= 2
            }
        }
    }
}
