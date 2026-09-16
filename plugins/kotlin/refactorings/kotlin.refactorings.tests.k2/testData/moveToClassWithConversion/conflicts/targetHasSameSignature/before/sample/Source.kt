package sample

class Target {
    fun foo() {
        println("existing")
    }
}

fun <caret>foo(t: Target) {
    println(t)
}
