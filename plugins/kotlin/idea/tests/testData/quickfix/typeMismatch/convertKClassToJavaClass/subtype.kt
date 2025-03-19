// "Convert 'KClass' to 'Class'" "false"
// WITH_STDLIB
// IGNORE_IRRELEVANT_ACTIONS
// DISABLE_ERRORS

fun main() {
    val cls: KClass<Int> = Int::class
    acceptClass(<caret>cls)
}

fun acceptClass(cls: Class<Number>) = Unit
