// "Make 'Companion' public explicitly" "true"
// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -Xexplicit-api=strict

public class Foo1() {
    companion <caret>object {}
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction