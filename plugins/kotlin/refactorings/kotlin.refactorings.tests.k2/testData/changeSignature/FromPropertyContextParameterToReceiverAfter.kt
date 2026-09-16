// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

interface Context {
    val isTrue: Boolean
}

val Context.prop: Boolen
    get() = isTrue

fun testContextScope(param: Context) {
    context(param) {
        contextOf<Context>().prop
    }
}

context(c: Context)
fun testForwardedContextParameter() {
    c.prop
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
        contextOf<Context>().prop
    }
}
