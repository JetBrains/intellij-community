// ENABLED_LANGUAGE_FEATURE: ContextParameters
// IGNORE_K1

inline class A(val p: Int)

context(x: A)
fun A.foo(y: A) {
    val z = A(4)
    //Breakpoint!
    val x = 1
}

fun main() {
    with(A(1)) { A(2).foo(A(3)) }
}

// EXPRESSION: x
// RESULT: instance of A(id=ID): LA;

// EXPRESSION: x.p
// RESULT: 1: I

// EXPRESSION: y
// RESULT: instance of A(id=ID): LA;

// EXPRESSION: y.p
// RESULT: 3: I

// EXPRESSION: this
// RESULT: instance of A(id=ID): LA;

// EXPRESSION: this.p
// RESULT: 2: I

// EXPRESSION: z
// RESULT: instance of A(id=ID): LA;

// EXPRESSION: z.p
// RESULT: 4: I