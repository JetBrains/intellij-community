// IGNORE_K1

// ATTACH_LIBRARY: specialJvmNames

import pack.*

fun foo() {
    func1()
    func2()
    func3()
    func4()
    func5()
    func6()
    func7()
}

fun main() {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: func1()
// RESULT: 1: I

// EXPRESSION: func2()
// RESULT: 2: I

// EXPRESSION: func3()
// RESULT: 3: I

// EXPRESSION: func4()
// RESULT: 4: I

// EXPRESSION: func5()
// RESULT: 5: I

// EXPRESSION: func6()
// RESULT: 6: I