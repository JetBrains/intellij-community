// "Remove unused '_' declaration" "true"
// COMPILER_ARGUMENTS: -Xreturn-value-checker=check
fun someUnitFunction() {}

fun test() {
    someUnitFunction()
    val <caret>_ = { println("ran") }()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.RemoveUnnamedPropertyFixFactory$RemoveUnnamedPropertyFix
