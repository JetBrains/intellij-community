interface Foo {
    fun baz(a: String)
}
interface Bar

fun foo(a: Foo.() -> Unit) {}
fun bar(a: Bar.() -> Unit) {}

fun Foo.baz() {
    baz("Ah")
}

fun foobs() {
    foo {
        bar {
            b<caret>az()
        }
    }
}