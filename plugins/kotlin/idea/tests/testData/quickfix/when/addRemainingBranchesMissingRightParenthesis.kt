// "Add remaining branches" "false"
// ERROR: 'when' expression must be exhaustive, add necessary 'is B' branch or 'else' branch instead
// WITH_STDLIB

sealed class A
class B : A()

fun test(a: A) {
  <caret>when (a
