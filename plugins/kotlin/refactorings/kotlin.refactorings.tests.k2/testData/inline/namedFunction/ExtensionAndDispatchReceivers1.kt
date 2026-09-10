class C {
    val refC = 42
}

class A {
    val cc = C()
    val refA = 42
    fun A.barA() = 42
    fun C.barC() = 42

    fun C.fo<caret>oC() {
        val sum = refA + refC + barA() + barC()
    }

    fun allInSameClass(c: C) {
        c.fooC()
    }

    fun allInSameClassOnProperty(c: C) {
        cc.fooC()
    }
}

fun A.allExtension(c: C) {
    c.fooC()
}