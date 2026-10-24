// "Specify '() -> Int' return type for enclosing function 'reproduce'" "false"
// "Apply all 'Change type' fixes in file" "false"
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
fun consume(block: () -> Unit) = block()

fun reproduce() {
    consume {
        return<caret>@consume 42
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix