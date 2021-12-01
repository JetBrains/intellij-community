// WITH_RUNTIME
@RequiresOptIn
annotation class Marker

@Marker
fun foo(x: Int): Int = x

@OptIn(Marker::class)
fun bar() {
    fun baz() {
        <selection>println(foo(1))</selection>
    }

    baz()
}
