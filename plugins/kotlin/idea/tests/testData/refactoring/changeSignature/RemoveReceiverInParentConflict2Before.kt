open class Odin {
    open fun String.jus<caret>tFun() = Unit
}

open class Dva : Odin() {
    fun another(s: String) = Unit
    override fun String.justFun() {

    }
}

class Three : Dva() {
    override fun String.justFun() {
        another(this)
    }
}