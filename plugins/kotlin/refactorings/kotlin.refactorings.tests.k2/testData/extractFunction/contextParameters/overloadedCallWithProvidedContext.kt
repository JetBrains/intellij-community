// COMPILER_ARGUMENTS: -Xcontext-parameters
context(c: Int)
fun foo() {
    <selection>with("context") {
        bar('x')
    }</selection>
}

context(s: String, i: Int) fun bar(c: Char) {}
context(s: String, i: Int) fun bar(b: Boolean) {}
