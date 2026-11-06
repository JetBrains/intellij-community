// CHECK_ERRORS_AFTER
fun bar(string: String, n: Int): Int = string.length + n

fun m(list: List<Int>, s: String?): List<Int?> = list.map { s?.let { string -> bar(string, it) } }
