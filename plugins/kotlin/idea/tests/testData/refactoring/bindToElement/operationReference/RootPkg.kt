// BIND_TO barFoo
fun foo() {
    0 <caret>fooBar 1
}

infix fun Int.fooBar(other: Int) { this + other }

infix fun Int.barFoo(other: Int) { this + other }