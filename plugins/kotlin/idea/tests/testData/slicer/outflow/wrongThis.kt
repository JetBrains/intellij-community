// FLOW: OUT
// WITH_STDLIB

class C {
    fun String.extensionFun(): Any {
        with("A") {
            println(this.length)
            println(this@extensionFun.length)
        }
        return this@C
    }

    fun foo() {
        val x = <caret>"".extensionFun()
    }
}
