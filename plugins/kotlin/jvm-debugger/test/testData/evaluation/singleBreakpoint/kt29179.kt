package kt29179

class A {
    val a = 1
    fun bar() = 2
    fun foo() {
        3
            //Breakpoint!
            .toString()
    }
}

fun main() {
    A().foo()
}

// Muted on the IR backend due to differences in stepping behavior.
// See [compiler/testData/debug/stepping/kt29179.kt] for evidence,
// and reevalute this once that is investigated.

// EXPRESSION: bar()
// RESULT: 2: I

// EXPRESSION: this
// RESULT: instance of kt29179.A(id=ID): Lkt29179/A;