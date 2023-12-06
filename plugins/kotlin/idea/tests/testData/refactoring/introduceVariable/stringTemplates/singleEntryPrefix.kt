// IGNORE_K2
fun foo(a: Int): String {
    val x = "xabc$a"
    val y = "${a}abcx"
    val z = "xacb$a"
    return "<selection>abc</selection>def"
}