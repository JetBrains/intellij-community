// COMPILER_ARGUMENTS: -Xcontext-receivers
// NEW_NAME: p
// RENAME: member

abstract class Foo {
    context(<caret>_: String)
    abstract fun foo(a: String)
}

class Bar : Foo() {
    context(_: String) override fun foo(a: String) {}
}