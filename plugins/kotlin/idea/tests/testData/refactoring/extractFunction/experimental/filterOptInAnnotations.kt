// WITH_RUNTIME
// PARAM_DESCRIPTOR: val z: kotlin.Int defined in baz
// PARAM_TYPES: kotlin.Int
@RequiresOptIn
annotation class Marker

@RequiresOptIn
annotation class Another

@Marker
fun foo(x: Int): Int = x

@Another
fun bar(x: Int) {
    println(x)
}

@OptIn(Marker::class, Another::class)
fun baz() {
    val z = foo(1)
    <selection>println(z)
    bar(z)</selection>
}
