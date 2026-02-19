// "Use withIndex() instead of manual index increment" "true"

fun foo(list: List<String>) {
    var i = 0
    <caret>for (s in list) {
        println(i)
        val x = s.length * i
        ++i
        if (x > 1000) break
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UseWithIndexInspection$createQuickFix$1