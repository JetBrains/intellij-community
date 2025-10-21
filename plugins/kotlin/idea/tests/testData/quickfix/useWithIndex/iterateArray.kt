// "Use withIndex() instead of manual index increment" "true"

fun foo(array: Array<String>): Int? {
    var index = 0
    <caret>for (s in array) {
        val x = s.length * index
        index++
        if (x > 0) return x
    }
    return null
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UseWithIndexInspection$createQuickFix$1