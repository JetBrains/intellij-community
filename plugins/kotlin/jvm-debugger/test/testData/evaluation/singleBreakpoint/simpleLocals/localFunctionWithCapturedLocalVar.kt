package localFunction

class A {
    fun test() {
        val x: Int = 42
        fun localFunction() = x + 8

        //Breakpoint!
        val a = 1
    }
}


fun main() {
    A().test()
}

// EXPRESSION: localFunction()
// RESULT: 50: I