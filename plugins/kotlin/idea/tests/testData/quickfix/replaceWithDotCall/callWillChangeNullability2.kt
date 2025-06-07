// "Replace with dot call" "true"
// LANGUAGE_VERSION: 1.6

fun test(x: String) {
    x?.le<caret>ngth
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix