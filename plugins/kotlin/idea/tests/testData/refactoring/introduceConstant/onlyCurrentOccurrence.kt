// EXTRACTION_TARGET: property with initializer
// NO_DUPLICATES
val a = 1

fun foo(): Int {
    val x = 2 + 3
    val y = 3 + 2 + 22
    val z = "2"
    return <selection>2</selection>
}
// IGNORE_K1