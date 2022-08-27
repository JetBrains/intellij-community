// NEW_NAME: bar

package test

fun function(): Int {
    val other = foo

    val foo<caret> = 10

    foo + foo

    run {
        val foo = 20

        foo + foo
    }
}
