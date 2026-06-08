// "Replace '==' with 'contentEquals'" "true"
fun foo() {
    val a = arrayOf("a", "b", "c")
    val b = arrayOf("a", "b", "c")
    if (a <caret>== b) {
    }
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceArrayEqualityOpWithContentEqualsFixFactory$ReplaceWithContentEqualsFix