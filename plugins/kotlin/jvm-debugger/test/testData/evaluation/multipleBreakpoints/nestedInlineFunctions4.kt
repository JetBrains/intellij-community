package nestedInlineFunctions4

inline fun foo(fooParam: Int, block: (Int) -> Unit) {
    var fooVar = 0
    block(fooParam)
}

inline fun flaf() {
    var flafVar = 0
    foo(0) {
        val x = 1
        foo(1) {
            val y = 2
			//Breakpoint!
			println()
        }
        foo(2) {
            val z = 3
			//Breakpoint!
			println()
        }
		//Breakpoint!
		println()
    }
}

fun main() {
	flaf()
}

// EXPRESSION: x + y + it
// RESULT: 4: I

// EXPRESSION: x + z + it
// RESULT: 6: I

// EXPRESSION: x + it
// RESULT: 1: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
