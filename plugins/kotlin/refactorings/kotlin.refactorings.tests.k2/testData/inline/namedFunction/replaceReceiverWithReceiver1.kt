class Foo {
    fun String.oldFun() {
        newFun()
    }

    fun String.newFun() {}
}

fun foo(init: Foo.() -> Unit) {}

fun test() {
    foo {
        "a".old<caret>Fun()
    }
}