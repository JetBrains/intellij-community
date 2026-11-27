// COMPILER_ARGUMENTS: -Xcontext-parameters

class Ctx

context(<caret>c: Ctx)
fun k(x: String): String = "ctx"

fun k(x: String): String = "plain"
