internal class Outer {
    fun test(c: C) {
        println(c.string)
        println(c.string)
    }

    class C {
        var string: String = ""
            private set

        fun report(s: String) {
            string = s
        }

        fun test() {
            println(string)
            println(string)
        }
    }
}
