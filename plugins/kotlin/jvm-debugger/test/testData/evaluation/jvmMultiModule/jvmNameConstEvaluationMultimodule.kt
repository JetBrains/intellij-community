// MODULE: jvm-lib
// FILE: a.kt

const val prefix = "prefix_"

@JvmName("${prefix}f1")
fun f1() = 42

var prop: Int = 0
    @JvmName("${prefix}getter")
    get() {
        return field + 1
    }
    @JvmName("${prefix}setter")
    set(value) {
        field = value + 1
    }


// MODULE: jvm-app(jvm-lib)
// FILE: b.kt

fun main() {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: f1()
// RESULT: 42: I

// EXPRESSION: prop = 1; prop
// RESULT: 3: I