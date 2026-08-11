package sample

class Target {
    val x: Int = 42
}

fun Target.<caret>foo() {
    println(this@foo.x)
}
