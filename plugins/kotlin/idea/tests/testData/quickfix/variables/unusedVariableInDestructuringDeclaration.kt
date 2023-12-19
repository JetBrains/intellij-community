// "Remove variable 'one'" "false"
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Rename to _
// ACTION: Specify all types explicitly in destructuring declaration
// ACTION: Specify type explicitly
fun test(condition: Boolean, foo: Foo) {
    val v = if (condition) {
        val (<caret>one, two) = foo
        two
    } else {
        null
    }
}

data class Foo(val one: String, val two: String)
