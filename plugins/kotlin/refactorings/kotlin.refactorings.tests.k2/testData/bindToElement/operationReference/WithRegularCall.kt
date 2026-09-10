// BIND_TO barFoo
fun foo() {
    0 foo<caret>Bar 1
}

infix fun Int.fooBar(other: Int) { this + other }

fun Int.barFoo(other: Int) { this + other }