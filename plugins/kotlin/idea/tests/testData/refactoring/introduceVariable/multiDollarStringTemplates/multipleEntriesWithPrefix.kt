// INPLACE_VARIABLE_NAME: s
fun foo(a: Int): String {
    val x = "+cd$a:${a + 1}efg"
    val y = "+cd$a${a + 1}efg"
    return $$"ab<selection>cd$$a:$${a + 1}ef</selection>"
}