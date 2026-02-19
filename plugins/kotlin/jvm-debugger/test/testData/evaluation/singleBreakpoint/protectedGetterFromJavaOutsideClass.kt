// FILE: AbJ.java

public abstract class AbJ {
    abstract String getMe();
}

// FILE: main.kt

abstract class AbK : AbJ() {
    override fun getMe(): String {
        return "OK"
    }
}

class Cl : AbK() {
    fun foo() {
        println()
    }
}

fun main() {
    //Breakpoint!
    Cl().foo()
}

// EXPRESSION: Cl().me
// RESULT: "OK": Ljava/lang/String;

// EXPRESSION: Cl().getMe()
// RESULT: "OK": Ljava/lang/String;
