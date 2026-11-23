// NEW_NAME: error
fun f() {
    try {
        println()
    } catch (e<caret>: RuntimeException) {
        throw e
    }
}
