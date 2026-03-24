// "Change type of 'f' to '(Int, Int) -> (String) -> Int'" "true"
// LANGUAGE_VERSION: 2.2
// K2_ERROR: Initializer type mismatch: expected '() -> Long', actual '(Int, Int) -> (String) -> Int'.
fun foo() {
    val f: () -> Long =<caret> {
        a: Int, b: Int ->
        val x = {s: String -> 42}
        if (true) x
        else if (true) x else {
            var y = 42
            if (true) x else x
        }
    }
}
// IGNORE_K1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix