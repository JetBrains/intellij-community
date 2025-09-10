// "Use withIndex() instead of manual index increment" "true"

fun foo(array: IntArray): Int? {
    var index = 0
    <caret>for (i in array) {
        val x = i * index
        index++
        if (x > 0) return x
    }
    return null
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UseWithIndexInspection$createQuickFix$1