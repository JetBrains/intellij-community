// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
context(s: String)
fun foo(x: Int) {
    <selection>x.toString() == s</selection>
    x.toString() == s
}