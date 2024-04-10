class A<T> {
    fun foo(): Int{
        //Breakpoint!
        return 42
    }
}

fun main() {
    A<String>().foo()
}

// EXPRESSION: run { 24 }
// RESULT: 24: I