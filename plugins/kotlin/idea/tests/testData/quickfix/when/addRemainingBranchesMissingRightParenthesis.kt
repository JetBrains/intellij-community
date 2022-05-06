// "Add remaining branches" "false"
// ACTION: Do not show return expression hints
// WITH_STDLIB

sealed class A
class B : A()

fun test(a: A) {
  <caret>when (a
