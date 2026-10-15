// COMPILER_ARGUMENTS: -Xcontext-parameters
context(c: String)
fun foo() {
    <selection>bar('x')</selection>
}

context(s: String) fun bar(c: Char) {}
context(s: Int) fun bar(b: Boolean) {}
