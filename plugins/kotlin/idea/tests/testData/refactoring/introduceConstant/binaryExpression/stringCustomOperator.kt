// EXTRACTION_TARGET: property with initializer

fun foo(): String {
    return <selection>"1"-1</selection>
}

operator fun String.minus(i: Int): String = this.substring(0, length - i)
