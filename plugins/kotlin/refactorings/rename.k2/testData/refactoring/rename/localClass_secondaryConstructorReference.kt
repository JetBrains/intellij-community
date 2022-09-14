// NEW_NAME: Bar

package test

fun function() {
    class Foo constructor(s: String) {
        constructor(i: Int): this(i.toString())
    }

    Foo<caret>(42)
}
