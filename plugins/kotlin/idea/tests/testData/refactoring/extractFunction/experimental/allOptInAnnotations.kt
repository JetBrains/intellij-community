// WITH_RUNTIME
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
    <selection>bar(foo(1))</selection>
}
