fun <caret>foo(): Int = 1

class A(val n: Int) {
    fun bar(): Int {
        return foo() + n
    }
}

// IGNORE_K2