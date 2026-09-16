// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

package pack

context(_: String)
val foo: Int
  get() = 1

fun usage() {
  context("foo") {
    val a = <caret>foo
  }
}
