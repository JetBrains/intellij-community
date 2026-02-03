open class Odin {
    open fun String.justFun<caret>() = Unit
}
class Dva : Odin() {
    fun another(s: String) = Unit

    override fun String.justFun() {
        another(this)
    }
}