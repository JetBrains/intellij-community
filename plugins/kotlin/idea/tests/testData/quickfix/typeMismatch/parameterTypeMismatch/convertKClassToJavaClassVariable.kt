// "Convert 'KClass' to 'Class'" "true"
// WITH_RUNTIME

fun main() {
    val clazz = String::class
    acceptClass(<caret>clazz)
}

fun acceptClass(cls: Class<*>) = Unit
