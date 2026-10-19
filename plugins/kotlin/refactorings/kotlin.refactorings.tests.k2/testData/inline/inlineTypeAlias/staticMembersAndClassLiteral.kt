typealias <caret>Abc = String

fun main() {
    val value = Abc()
    val comparator = Abc.CASE_INSENSITIVE_ORDER
    val formatted = Abc.format("%s", value)
    val javaClass = Abc::class.java
    val kotlinClass = Abc::class
    println(listOf(comparator, formatted, javaClass, kotlinClass))
}
