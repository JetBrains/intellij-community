class Foo {
    fun String.oldFun() {
        this@oldFun.newFun()
    }

    fun String.newFun() {}
}

fun foo(init: Foo.() -> Unit) {}

fun test() {
    foo {
        "a".old<caret>Fun()
    }
}

// IGNORE_K1