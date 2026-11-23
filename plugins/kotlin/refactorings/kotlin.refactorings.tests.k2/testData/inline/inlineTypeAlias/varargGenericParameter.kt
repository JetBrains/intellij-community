typealias <caret>Items<T> = List<T>

fun takeItems(vararg xs: Items<String>): List<Items<String>> = xs.toList()
