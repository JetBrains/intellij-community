interface A {
    fun a() {}
}

class Dup : A {
    inner class Dup: A {
        fun foo() {
            super@D<caret>up.a()
        }
    }
}
// MULTIRESOLVE
// REF: (<root>).Dup
// REF: (in Dup).Dup
