open class Odin {
    open fun justFun<caret>(s: String) = Unit
}

open class Dva : Odin() {
    fun another(s: String) = Unit
    override fun justFun(s: String) {

    }
}

class Three : Dva() {
    override fun justFun(d: String) {
        another(d)
    }
}