// TARGET_CONTAINER: fun f()
// TARGET_CONTAINER: fun foo(x: Any, n: String)
// TARGET_CONTAINER: class Foo
// TARGET_CONTAINER: targetRendering.kt
@Deprecated("smth") class Foo {
    @Deprecated("xxx") fun foo(x: Any, n: String) {
        fun f() {
            <selection>val a = "".takeIf { true } </selection>
        }
    }
}