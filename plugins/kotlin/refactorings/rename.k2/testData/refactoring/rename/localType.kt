// NEW_NAME: Bar

package test

fun function(): Int {
    val miss: Foo = Foo()

    class Foo<caret>

    fun foo(f: Foo): Foo = Foo()

    val ref = ::Foo

    run {
        class Foo

        fun bar(foo: Foo): Foo = Foo()
    }
}
