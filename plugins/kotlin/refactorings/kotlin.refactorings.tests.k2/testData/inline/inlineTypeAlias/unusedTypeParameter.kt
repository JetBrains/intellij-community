class Box(val value: String)

typealias <caret>Alias<T> = Box

fun use() {
    val explicit = Alias<String>("one")
    val inferred = Alias("two")
    println(listOf(explicit, inferred))
}
