// "Add remaining branches" "true"
// WITH_STDLIB

sealed class A
class B : A()

fun test(a: A) {
  val i = w<caret>hen (a) {
