// "Change type of 'a3' to '(String) -> Unit'" "true"
// WITH_STDLIB
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

fun String.onlyNonNull() {}

val a3: (String?) -> Unit = String::onlyNonNull<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix
