// "Convert too long character literal to string" "false"
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Introduce local variable
// ERROR: Illegal escape: '\ '

fun foo() {
    "\ <caret>"
}
