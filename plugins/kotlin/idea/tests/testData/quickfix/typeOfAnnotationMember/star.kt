// "Replace array of boxed with array of primitive" "false"
// WITH_STDLIB
// ERROR: Invalid type of annotation member
// ACTION: Add full qualifier
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Put parameters on one line
annotation class SuperAnnotation(
        val foo: <caret>Array<*>,
        val str: Array<String>
)