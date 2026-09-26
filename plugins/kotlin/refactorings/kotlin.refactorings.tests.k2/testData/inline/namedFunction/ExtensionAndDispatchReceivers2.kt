class A {
    val refA = 42
    fun A.barA() = 42
    fun C.barC() = 42

    fun f<caret>ooA(c: C) {
        val sum = refA + c.refC + barA() + c.barC()
    }

    fun allInClass() {
        fooA(C())
    }
}

class C {
    val refC = 42
}

fun A.allInExtension(c: C) {
    fooA(c)
}