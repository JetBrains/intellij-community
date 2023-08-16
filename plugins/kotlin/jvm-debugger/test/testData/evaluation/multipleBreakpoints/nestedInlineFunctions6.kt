package nestedInlineFunctions6

inline fun foo(f: (Int) -> Unit) {
    val x6 = 6
    f(8)
    val x7 = 7
	//Breakpoint!
	println()
}

inline fun bar() {
    val x0 = 0
    x1()
}

inline fun baz() {
    x4()
    x5()
}

inline fun x1() {
    val x1 = 1
    x2()
    val x3 = 3
	//Breakpoint!
	println()
}

inline fun x2() {
    val x2 = 2
	//Breakpoint!
	println()
}

inline fun x4() {
    val x4 = 4
	//Breakpoint!
	println()
}

inline fun x5() {
    val x5 = 5
	//Breakpoint!
	println()
}

fun main() {
    val m = -1
    bar()
    foo {
        val x8 = 8
		//Breakpoint!
		println()
    }
    baz()
}

// EXPRESSION: x2
// RESULT: 2: I

// EXPRESSION: x1 + x3
// RESULT: 4: I

// EXPRESSION: x8 + m
// RESULT: 7: I

// EXPRESSION: x6 + x7
// RESULT: 13: I

// EXPRESSION: x4
// RESULT: 4: I

// EXPRESSION: x5
// RESULT: 5: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
