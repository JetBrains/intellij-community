class Dup {
    fun String.Dup() : Unit {
        this@D<caret>up
    }
}
// MULTIRESOLVE
// REF: (<root>).Dup
// REF: (for String in Dup).Dup()
