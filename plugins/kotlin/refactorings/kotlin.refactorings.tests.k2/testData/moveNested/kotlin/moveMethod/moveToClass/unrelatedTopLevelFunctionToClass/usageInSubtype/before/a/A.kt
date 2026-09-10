package a

fun b<caret>() {}

open class A {
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
