@JvmName("prefix_f1")
fun f1() = 42

var prop: Int = 0
    @JvmName("prefix_getter")
    get() {
        return field + 1
    }
    @JvmName("prefix_setter")
    set(value) {
        field = value + 1
    }

fun main() {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: f1()
// RESULT: 42: I

// EXPRESSION: prop = 1; prop
// RESULT: 3: I