// "Increase language version to 2.4" "false"
// LANGUAGE_VERSION: 2.3
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

annotation class Anno

@<caret>all:Anno
val a = 0
