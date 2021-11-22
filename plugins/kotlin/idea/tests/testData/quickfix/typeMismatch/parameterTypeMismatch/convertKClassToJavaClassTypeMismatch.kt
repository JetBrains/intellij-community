// "Convert 'KClass' to 'Class'" "false"
// WITH_RUNTIME
// IGNORE_IRRELEVANT_ACTIONS
// DISABLE-ERRORS

fun main() {
    val cls = String::class
    acceptClass(<caret>cls)
}

fun acceptClass(cls: Class<Int>) = Unit
