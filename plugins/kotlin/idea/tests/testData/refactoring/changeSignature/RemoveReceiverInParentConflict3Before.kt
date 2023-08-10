open class Odin {
    open fun String.ju<caret>stFun() = Unit
}

open class Dva : Odin() {
    fun another(s: String) = Unit
    override fun String.justFun() {
        another(this)
    }
}

class Three : Dva() {
    override fun String.justFun() {
        another(this)
    }
}