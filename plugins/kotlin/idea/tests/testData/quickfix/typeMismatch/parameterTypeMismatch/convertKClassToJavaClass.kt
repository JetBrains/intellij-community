// "Convert 'KClass' to 'Class'" "true"
// WITH_STDLIB

fun main() {
    acceptClass(<caret>String::class)
}

fun acceptClass(cls: Class<*>) = Unit
