// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

package pack

context(b: String)
val foo: String
  get() = b

fun usage() {
  context("foo") {
    val a = <caret>foo
  }
}
