package foo

fun other() {

}

class Foo {
    fun other()
}

fun bar<caret>(foo: Foo) {
    foo.other()
}
