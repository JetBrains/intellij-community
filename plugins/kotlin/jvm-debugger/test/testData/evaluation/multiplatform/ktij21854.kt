
// MODULE: common
// PLATFORM: common

// FILE: common.kt

fun test() {
    val x = 1
    fun bar(): Int {
        val y = x
        return y
    }
    val s = "OK"

    //Breakpoint1
    return
}

// ADDITIONAL_BREAKPOINT: common.kt / Breakpoint1

// EXPRESSION: s
// RESULT: "OK": Ljava/lang/String;


// MODULE: jvm(common)
// PLATFORM: jvm


// FILE: jvm.kt

fun main() {
    test()
}
