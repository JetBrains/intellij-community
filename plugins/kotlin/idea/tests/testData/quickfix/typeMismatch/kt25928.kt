// "Let 'Foo?' extend interface 'Foo'" "false"
// ACTION: Add non-null asserted (foo!!) call
// ACTION: Change type of 'x' to 'Foo?'
// ERROR: Type mismatch: inferred type is Foo? but Foo was expected
interface Foo

fun test(foo: Foo?) {
    val x: Foo = foo<caret>
}