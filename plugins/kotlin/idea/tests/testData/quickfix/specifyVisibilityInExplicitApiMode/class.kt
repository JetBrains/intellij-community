// "Make 'Foo1' public explicitly" "true"
// COMPILER_ARGUMENTS: -Xexplicit-api=strict

class <caret>Foo1() {}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicExplicitlyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeVisibilityFixFactories$getApplicator$1