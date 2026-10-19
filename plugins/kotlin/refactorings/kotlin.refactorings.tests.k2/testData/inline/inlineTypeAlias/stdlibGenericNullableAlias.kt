// ASSERT_LIBRARY_ALIAS_SURVIVES

fun main() {
    val list: kotlin.collections.<caret>ArrayList<String>? = null
    println(list)
}
