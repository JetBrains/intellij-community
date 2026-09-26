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
        //Breakpoint!
        println()
    }
}

fun main() {
    Cl().foo()
}

// EXPRESSION: me
// RESULT: "OK": Ljava/lang/String;

// EXPRESSION: getMe()
// RESULT: "OK": Ljava/lang/String;
