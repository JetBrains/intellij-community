package notCapturedFunParameter

fun main() {
    App("", "")
}

class App(val name: String) {
    constructor(name: String, parent: String) : this(name) {
        block {
            // EXPRESSION: parent
            // RESULT: 'parent' is not captured
            //Breakpoint!
            println()
        }
    }
}

fun block(block: () -> Unit) {
    block()
}