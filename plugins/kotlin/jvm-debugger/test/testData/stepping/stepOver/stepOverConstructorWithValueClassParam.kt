package stepOverConstructorWithValueClassParam

class Clazz(var name: String, var age: UInt) {
    init {
        // STEP_OVER: 3
        //Breakpoint!
    }
}

fun main() {
    Clazz("hello", 42u)
}
