// "Increase language version to 2.5" "true"
// LANGUAGE_VERSION: 2.4
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS
// WITH_STDLIB

val <caret>u = 1u + 1u
// IGNORE_K2
// TODO: KT-90212 has to fix it
