open class Odin {
    open fun justFun<caret>(s: String) = Unit
}

class Dva : Odin() {
    fun another(s: String) = Unit
    override fun justFun(s: String) {
        another(s)
    }
}