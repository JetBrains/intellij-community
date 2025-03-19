package internalInlineMethod

fun main() {
    val clazz = Clazz()
    //Breakpoint!
    clazz.inlineInternalFun() + clazz.inlineInternalFun()
}

class Clazz() {
    internal inline fun inlineInternalFun() = 30
}
