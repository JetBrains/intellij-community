class Foo(var bar: Int)

fun test(foo: Foo) {
    <selection>foo.bar</selection> = 1
    foo.bar = foo.bar + 1
    ()
}