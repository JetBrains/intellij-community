fun String.renamed(): Int = length

fun test() {
    val f: (String) -> Int = String::renamed
    f("a")
}