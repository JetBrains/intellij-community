// "Use withIndex() instead of manual index increment" "true"

fun foo(list: List<String>): Int? {
    var index: Int
    index = 0
    <caret>for (s in list) {
        val x = s.length * index
        index++
        if (x > 0) return x
    }
    return null
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UseWithIndexInspection$createQuickFix$1