// "Add remaining branches" "false"
// WITH_STDLIB

sealed class A
class B : A()

fun test(a: A) {
  <caret>when (a
