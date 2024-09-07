// "Remove '.java'" "false"
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is Class<Foo> but String was expected
fun foo() {
    bar(Foo::class.java<caret>)
}

class Foo

fun bar(s: String) {
}