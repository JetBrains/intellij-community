// WITH_DEFAULT_VALUE: false

import kotlin.reflect.KCallable


class ExampleClass

private var KCallable<*>.isAccessible: kotlin.Boolean
    get() {
        TODO()
    }
    set(value) {}

fun m1() {
    val privateField = <selection>ExampleClass::class.members
        .first()
        .apply { isAccessible = true }</selection>
}

fun m() {
    m1()
}