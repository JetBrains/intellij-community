// COMPILER_ARGUMENTS: -Xcontext-parameters

class Ctx

class A {
    context(<caret>c: Ctx)
    fun k(x: String): String = "ctx"

    fun k(x: Any): String = "any"
}

fun callK(a: A): String = a.k("s")
