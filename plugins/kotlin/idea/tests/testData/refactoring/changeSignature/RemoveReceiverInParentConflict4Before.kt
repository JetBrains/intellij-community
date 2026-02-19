open class Odin {
    open fun Int.justFun<caret>(s: String) = Unit
}

open class Dva : Odin() {
    fun another(s: String) = Unit
    override fun Int.justFun(s: String) {
        another(s)
    }
}

class Three : Dva() {
    override fun Int.justFun(d: String) {
        another(d)
        print(this)
    }
}