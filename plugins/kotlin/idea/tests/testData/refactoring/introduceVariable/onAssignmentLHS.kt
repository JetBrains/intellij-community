// WITH_STDLIB
class Foo(var bar: Int)

fun test() {
    val foo = Foo(1)
    println(foo.bar)
    <selection>foo.bar</selection> = foo.bar + 1
}