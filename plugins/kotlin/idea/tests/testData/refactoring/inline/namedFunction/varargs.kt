// WITH_STDLIB

fun main(args: Array<String>) {
    val list = lis<caret>tOf(1, 2, 3, 4, 5)
    val foundElement = list.firstOrNull { it: Int -> it > 3 }
}

// IGNORE_K1