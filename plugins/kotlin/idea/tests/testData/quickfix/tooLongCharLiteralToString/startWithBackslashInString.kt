// "Convert too long character literal to string" "false"
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Convert to 'buildString' call
// ACTION: Introduce local variable
// ACTION: To raw string literal
// ERROR: Illegal escape: '\ '

fun foo() {
    "\ <caret>"
}