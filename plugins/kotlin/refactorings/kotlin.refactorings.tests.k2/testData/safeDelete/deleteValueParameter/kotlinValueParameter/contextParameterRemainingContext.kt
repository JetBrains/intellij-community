// COMPILER_ARGUMENTS: -Xcontext-parameters

class Ctx
class Other

context(<caret>c: Ctx, other: Other)
fun k(x: String): String = "ctx"

fun k(x: Any): String = "any"

fun callK(): String = k("s")
