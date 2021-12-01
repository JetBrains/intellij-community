// WITH_RUNTIME
@RequiresOptIn
annotation class Marker

@Marker
fun foo(x: Int): Int = x

@Marker
fun bar() {
    <selection>println(foo(1))</selection>
}
