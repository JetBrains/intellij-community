package stepOutConstructorWithValueClassParam

class Clazz(var name: String, var age: UInt) {
    init {
        // STEP_OUT: 1
        // RESUME: 1
        //Breakpoint!
    }
}

fun main() {
    Clazz("hello", 42u)
}
