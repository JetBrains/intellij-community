// "Make 'Foo1' public explicitly" "true"
// PRIORITY: HIGH
// COMPILER_ARGUMENTS: -Xexplicit-api=strict

class <caret>Foo1() {}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction