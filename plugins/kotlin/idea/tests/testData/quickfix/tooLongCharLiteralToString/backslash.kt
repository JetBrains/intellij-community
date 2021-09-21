// "Convert too long character literal to string" "false"
// ACTION: Compiler warning 'UNUSED_EXPRESSION'
// ACTION: Introduce local variable
// ERROR: Illegal escape: ''\''

fun foo() {
    '\'<caret>
}