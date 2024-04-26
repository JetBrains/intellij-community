val x = 42
fun callUsage() {
    when ((<caret>x)) {
        else -> { println(6) }
    }
}