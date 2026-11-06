// CHECK_ERRORS_AFTER
fun String.<caret>bar(n: Int): Int = length + n

fun m(list: List<Int>, s: String?): List<Int?> = list.map { s?.bar(it) }
