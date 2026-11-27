// COMPILER_ARGUMENTS: -Xcontext-parameters

class Ctx

context(<caret>c: Ctx)
val String.tag: String get() = "ctx"

val String.tag: String get() = "plain"
