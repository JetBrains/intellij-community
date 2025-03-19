class A {
    operator fun unaryPlus(): Boolean = true
    operator fun unaryMinus(): Boolean = false
}

fun foo() {
    <caret>+A() || -A()
}
// EXISTS: unaryPlus(), constructor A()_0, unaryMinus(), constructor A()_1
