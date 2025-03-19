fun main(args: Array<String>) {
    A("foo")
}

//FunctionBreakpoint!
class A(val a: String) {
    val b = 1
    val c: Int
        get() = 2
}

// EXPRESSION: a
// RESULT: "foo": Ljava/lang/String;

// EXPRESSION: this.a
// RESULT: "foo": Ljava/lang/String;

// EXPRESSION: b
// RESULT: 0: I

// EXPRESSION: this.b
// RESULT: 0: I

// EXPRESSION: c
// RESULT: 2: I

// EXPRESSION: this.c
// RESULT: 2: I

// IGNORE_K2