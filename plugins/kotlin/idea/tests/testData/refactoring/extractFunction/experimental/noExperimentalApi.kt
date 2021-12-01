// WITH_RUNTIME
// PARAM_DESCRIPTOR: val y: kotlin.Int defined in bar
// PARAM_TYPES: kotlin.Int

@RequiresOptIn
annotation class Marker

@Marker
fun foo(x: Int): Int = x + 1

fun baz(x: Int): Unit {
    println(x)
}

@OptIn(Marker::class)
fun bar() {
    val y = foo(1)
    <selection>baz(y)</selection>
}
