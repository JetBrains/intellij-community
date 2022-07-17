// "Convert 'KClass' to 'Class'" "true"
// WITH_STDLIB

fun main() {
    val clazz = String::class
    acceptClass(<caret>clazz)
}

fun acceptClass(cls: Class<*>) = Unit
