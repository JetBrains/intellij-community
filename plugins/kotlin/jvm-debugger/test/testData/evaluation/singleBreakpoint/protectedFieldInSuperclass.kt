package protectedFieldInSuperclass

open class Base {
    protected var pro: Int = 1
}

class Sub : Base() {
    fun bp() {
        //Breakpoint!
        Unit
    }
}

fun main() {
    Sub().bp()
}

// EXPRESSION: pro
// RESULT: 1: I

// EXPRESSION: pro_field
// RESULT: 1: I

// Until field labels are supported
// IGNORE_K2