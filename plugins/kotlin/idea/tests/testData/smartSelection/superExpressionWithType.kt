open class EE() {
    open fun f() = 43
}

class FF() : EE() {
    override fun f() = <caret>super<EE>.f() - 1
}
/*
super<EE>.f()
super<EE>.f() - 1
*/