// "Use withIndex() instead of manual index increment" "false"

fun foo(list: List<String>): Int? {
    var index = 0
    <caret>for (s in list) {
        val x = s.length * index
        index++
        println(index)
        if (x > 0) return x
    }
    return null
}