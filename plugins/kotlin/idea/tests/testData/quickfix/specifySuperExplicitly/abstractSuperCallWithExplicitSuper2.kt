// "Specify super type 'IntA' explicitly" "true"

interface IntA {
    fun check(): String = "OK"
}

interface IntB {
    fun check(): String
}

abstract class AbstractClassA {
    abstract fun check(): String
}

abstract class DerivedA : AbstractClassA(), IntA

class DerivedB : DerivedA(), IntB, IntA {

    override fun check(): String {
        // Dispatched to AbstractClassA.check()
        return super<DerivedA>.<caret>check()
    }
}
