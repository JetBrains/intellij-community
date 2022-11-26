// WITH_STDLIB 1.7.0
// LANGUAGE_VERSION 1.6
@Experimental
annotation class Marker

@Marker
fun foo(x: Int): Int = x

@UseExperimental(Marker::class)
fun bar() {
    <selection>println(foo(1))</selection>
}
