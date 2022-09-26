// NEW_NAME: bar

package test

fun function(): Int {
    foo()

    fun foo<caret>(): Int {}

    foo() + (::foo).invoke()

    run {
        fun foo(s: String): Int {}

        foo("") + foo()
    }
}
