fun main() {
    A().foo()
}

class A {
    fun foo() {
        //Breakpoint!
        val x = 1
    }
}

// EXPRESSION: super.toString().substring(0, 2)
// RESULT: "A@": Ljava/lang/String;

// EXPRESSION: super.hashCode() == hashCode()
// RESULT: 1: Z

// EXPRESSION: super.equals(this)
// RESULT: 1: Z