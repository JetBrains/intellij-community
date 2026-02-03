// EXTRACTION_TARGET: property with initializer
val a = 1

fun foo(): String {
    val x = "xyfalsez"
    val y = "xyFalsez"
    val z = false
    return "ab<selection>false</selection>def"
}