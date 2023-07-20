// "Change parameter 'z' type of function 'foo' to '(Int) -> String'" "false"
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Do not show return expression hints
// ACTION: Introduce local variable

fun foo(y: Int = 0, z: (Int) -> String = {""}) {
    foo {
        ""<caret> as Int
        ""
    }
}
