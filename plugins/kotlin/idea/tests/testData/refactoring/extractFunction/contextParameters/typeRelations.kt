// COMPILER_ARGUMENTS: -Xcontext-parameters
interface A
interface B: A

context(a: A)
fun foo() {}

context(b: B)
fun bar() {}

context(b: B)
fun baz() {
    <selection>if (b != null) {
        foo()
        bar()
    }</selection>
}

// IGNORE_K1


