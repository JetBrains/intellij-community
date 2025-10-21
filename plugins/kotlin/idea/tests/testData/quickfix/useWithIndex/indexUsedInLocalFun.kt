// "Use withIndex() instead of manual index increment" "false"

fun foo(list: List<String>): Int? {
    var index: Int = 0
    fun local() {
        println(index)
    }
    <caret>for (s in list) {
        val x = s.length * index
        index++
        if (x > 0) return x
    }
    return null
}
