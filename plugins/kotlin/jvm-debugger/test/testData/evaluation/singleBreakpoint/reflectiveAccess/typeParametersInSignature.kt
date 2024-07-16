class Main {
    fun foo(): Int {
        //Breakpoint!
        return 0
    }
}

private fun <T> bar1(x : T) = 42

private fun <T> T.bar2() = 43

fun main() {
    Main().foo()
}

// EXPRESSION: bar1("")
// RESULT: 42: I

// EXPRESSION: "".bar2()
// RESULT: 43: I