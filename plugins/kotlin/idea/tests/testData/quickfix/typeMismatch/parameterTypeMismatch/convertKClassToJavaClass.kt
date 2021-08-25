// "Convert 'KClass' to 'Class'" "true"
// WITH_RUNTIME

fun main() {
    acceptClass(<caret>String::class)
}

fun acceptClass(cls: Class<*>) = Unit
