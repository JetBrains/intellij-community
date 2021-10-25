// "Specify supertype" "true"
// SHOULD_DIFFER_FROM_FE10
// DISABLE-ERRORS
interface Z {
    fun foo(): CharSequence = ""
}

open class Y {
    override fun foo(): String = ""
}

class Test : Z, Y() {
    override fun foo(): String {
        // FE1.0 checks whether return type matches so it offers `Y`. FIR doesn't do such fancy checks and offers both `Z` and `Y`, in which
        // case, the first entry is chosen by the test framework.
        return <caret>super.foo()
    }
}
