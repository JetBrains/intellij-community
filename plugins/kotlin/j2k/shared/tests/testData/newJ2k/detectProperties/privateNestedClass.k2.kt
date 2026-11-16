// ERROR: EXPOSED_PARAMETER_TYPE
internal class Outer {
    fun test(c: C) {
        println(c.string)
        println(c.string)
    }

    private class C {
        var string: String? = ""
            private set

        fun report(s: String?) {
            this.string = s
        }

        fun test() {
            println(this.string)
            println(this.string)
        }
    }
}
