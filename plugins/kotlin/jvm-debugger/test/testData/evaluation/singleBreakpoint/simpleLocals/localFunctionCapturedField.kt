package localFunctionCapturedField

class A(val x: Int) {

    fun test() {
        fun localFunction() = x + 8

        //Breakpoint!
        val a = 1
    }
}


fun main() {
    A(50).test()
}

// EXPRESSION: localFunction()
// RESULT: 58: I