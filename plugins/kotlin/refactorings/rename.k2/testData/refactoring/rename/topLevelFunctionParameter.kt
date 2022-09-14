// NEW_NAME: bar

package test

fun otherUsage() {
    function(foo = 10)
    function(foo = "")
}

fun function(foo: String) {}

fun function(foo<caret>: Int): Int {
    foo + foo
}
