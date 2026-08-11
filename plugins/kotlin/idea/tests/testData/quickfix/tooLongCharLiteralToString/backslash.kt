// "Convert too long character literal to string" "false"
// ACTION: Compiler warning 'UNUSED_EXPRESSION' options
// ACTION: Introduce local variable
// ERROR: Illegal escape: ''\''
// K2_AFTER_ERROR: ILLEGAL_ESCAPE
// K2_ERROR: ILLEGAL_ESCAPE

fun foo() {
    '\'<caret>
}