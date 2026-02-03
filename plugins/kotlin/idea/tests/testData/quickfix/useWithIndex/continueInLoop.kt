// "Use withIndex() instead of manual index increment" "false"

fun foo(list: List<String>): Int? {
    var index = 0
    <caret>for (s in list) {
        if (maybeSkip()) continue
        val x = s.length * index
        index++
        if (x > 0) return x
    }
    return null
}

fun maybeSkip(): Boolean = TODO("Omitted")
