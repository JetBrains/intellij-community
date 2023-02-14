// "Cast expression 'listOf(1)' to 'List<T>'" "true"
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is IntegerLiteralType[Int,Long,Byte,Short] but T was expected
// IGNORE_FE10
fun <T> f() {
    val someList: List<T> = lis<caret>tOf(1)
}