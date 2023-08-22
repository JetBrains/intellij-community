// "Replace array of boxed with array of primitive" "false"
// WITH_STDLIB
// ACTION: Add full qualifier
// ACTION: Convert to vararg parameter (may break code)
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Introduce import alias
// ACTION: Put parameters on one line
annotation class SuperAnnotation(
        val str: <caret>Array<String>
)