// FLOW: OUT

private fun foo(a: Int, b: Int) {
    val result = a
}

private fun bar(<caret>x: Int) {
    foo(b = 1, a = x)
}