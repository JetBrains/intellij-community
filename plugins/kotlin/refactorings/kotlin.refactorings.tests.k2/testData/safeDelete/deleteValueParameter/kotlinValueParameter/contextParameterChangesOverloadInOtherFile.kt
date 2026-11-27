// COMPILER_ARGUMENTS: -Xcontext-parameters
package p

class Ctx

context(<caret>c: Ctx)
fun k(x: String): String = "ctx"

fun k(x: Any): String = "any"
