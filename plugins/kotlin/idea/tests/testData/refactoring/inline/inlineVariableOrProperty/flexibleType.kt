class B {
    val some<caret>Field: String? = getSomeFiled()
    fun getSomeFiled(): String = "1"
    val nested: B? = null
}

class Usage(
    val javaClass: JavaClass,
    val b: B,
) {
    fun getSomeField() = javaClass.b?.someField

    fun getSomeField2() = b.nested?.someField
}

// IGNORE_K1