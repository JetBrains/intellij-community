// "Change return type of enclosing function 'b' to 'Array<Array<Int>>'" "true"
// WITH_STDLIB
val a: Array<Int> = arrayOf(1)
fun b(): Array<Int> {
    return <caret>arrayOf(a)
}
