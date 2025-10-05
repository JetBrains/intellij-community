// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

interface Context {
    val isTrue: Boolean
}

context(<caret>c: Context)
val prop: Boolen
    get() = c.isTrue

fun testContextScope(param: Context) {
    context(param) {
        prop
    }
}

context(c: Context)
fun testForwardedContextParameter() {
    prop
}

fun testWithScope(c: Context) {
    with(c) {
        prop
    }
}

fun Context.testExtensionReceiver() {
    prop
}

abstract class AbstractContext : Context {
    fun testDispatchReceiver() {
        prop
    }
}
