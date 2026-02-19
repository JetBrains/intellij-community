// "Configure arguments for the feature: annotation all use site target" "true"
// LANGUAGE_VERSION: 2.2
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

annotation class Anno

@<caret>all:Anno
val a = 0