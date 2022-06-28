// "Change return type of called function 'AA.f' to 'Boolean'" "false"
// ACTION: Change return type of enclosing function 'AAA.g' to 'Int'
// ACTION: Do not show return expression hints
// ERROR: Type mismatch: inferred type is Int but Boolean was expected
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