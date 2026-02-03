package a

fun `regular`() {}
fun `wh<caret>ile`() {}

fun referNames2() {
    `regular`()
    `while`()
}