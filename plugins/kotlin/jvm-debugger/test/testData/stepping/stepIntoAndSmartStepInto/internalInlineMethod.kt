package internalInlineMethod

fun main() {
    val clazz = Clazz()
    //Breakpoint!
    clazz.inlineInternalFun() + clazz.inlineInternalFun()
}

class Clazz() {
    internal inline fun inlineInternalFun() = 30
}

// IGNORE_K2_SMART_STEP_INTO
// Remove after IDEA-326256 fix
