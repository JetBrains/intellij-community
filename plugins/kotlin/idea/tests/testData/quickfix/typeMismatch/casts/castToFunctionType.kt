// "Cast expression 'x' to '() -> Int'" "true"
// K2_ERROR: Return type mismatch: expected '() -> Int', actual 'Any'.
fun foo(x: Any): () -> Int {
    return x<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CastExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.CastExpressionFixFactories$CastExpressionModCommandAction