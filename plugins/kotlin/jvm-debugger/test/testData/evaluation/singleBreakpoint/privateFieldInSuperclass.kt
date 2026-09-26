package protectedFieldInSuperclass

open class Base {
    private var pri: Long = 2L
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

// EXPRESSION: pri
// RESULT: 2: J

// EXPRESSION: pri_field
// RESULT: 2: J

// Until field labels are supported
// IGNORE_K2