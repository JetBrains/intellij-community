package a

open class A {
    fun b() {}
}

open class B : A() {
    fun directSubtype() {
        b()
    }

    inner class SubtypeInnerClass {
        fun subtypeInnerClassMethod() {
            b()
        }
    }

    class SubtypeNestedClass {
        fun subtypeNestedClassMethod() {
            b()
        }
    }
}

fun B.directSubtypeExtension() {
    b()
}

object C : B() {
    fun transitiveObjectSubtype() {
        b()
    }
}

fun C.objectExtension() {
    b()
}
