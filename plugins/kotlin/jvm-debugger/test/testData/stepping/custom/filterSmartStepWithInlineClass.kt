package filterSmartStepWithInlineClass

@JvmInline
private value class A(val s: String) {
    fun foo(): String {
        return s
    }
}

fun String.bar() = println(this)

fun main() {
    val a = A("a")
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    //Breakpoint!
    a.foo().bar()
}
