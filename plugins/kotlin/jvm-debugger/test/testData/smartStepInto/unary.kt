class A {
    operator fun unaryPlus(): Boolean = true
    operator fun unaryMinus(): Boolean = false
}

fun foo() {
    <caret>+A() || -A()
}
// EXISTS: unaryPlus(), unaryMinus()
