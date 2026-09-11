// "Change return type of enclosing function 'foo' to 'Any'" "true"
// WITH_STDLIB
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
fun foo(n: Int): Boolean {
    if (true) return "foo"<caret>
    n.let {
        return@foo 1
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix