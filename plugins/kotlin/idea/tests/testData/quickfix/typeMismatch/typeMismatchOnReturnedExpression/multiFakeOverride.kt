// "Change return type of called function 'AA.f' to 'Boolean'" "false"
// ACTION: Change return type of enclosing function 'AAA.g' to 'Int'
// ERROR: Type mismatch: inferred type is Int but Boolean was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'Boolean', actual 'Int'.
interface A {
    fun f(): Int
}

open class AA {
    fun f() = 3
}

class AAA: AA(), A {
    fun g(): Boolean {
        return f<caret>()
    }
}