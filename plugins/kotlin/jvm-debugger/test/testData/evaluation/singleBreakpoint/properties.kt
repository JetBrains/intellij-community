class A {
    private val p1 = 1

    @JvmField
    val p2 = 2

    val p3 get() = 3

    val p4 inline get() = 4

    private val p5 get() = 5

    private val p6 inline get() = 6

    private var p7 = 0

    private var p8: Int = 0
        get() = field + 1
        set(value) { field = value + 1; }

    private var p9: Int
        inline get() = p7 + 1
        inline set(value) { p7 = value; }

    @JvmField
    protected val p10 = 10

    @JvmField
    internal val p11 = 11

    companion object {
        @JvmStatic
        private val op1 = 101

        @JvmStatic
        private val op2 get() = 102

        private val op3 inline get() = 103

        @JvmStatic
        private var op4 = 0

        @JvmStatic
        private var op5: Int = 0
            get() = field + 1
            set(value) { field = value + 1; }

        private var op6: Int
            inline get() = op4 + 1
            inline set(value) { op4 = value; }
    }
}

fun main(args: Array<String>) {
    //Breakpoint!
    args.size
}

// EXPRESSION: A().p1
// RESULT: 1: I

// EXPRESSION: A().p2
// RESULT: 2: I

// EXPRESSION: A().p3
// RESULT: 3: I

// EXPRESSION: A().p4
// RESULT: 4: I

// EXPRESSION: A().p5
// RESULT: 5: I

// EXPRESSION: A().p6
// RESULT: 6: I

// EXPRESSION: A().let { it.p7 = 7; it.p7 }
// RESULT: 7: I

// EXPRESSION: A().let { it.p8 = 8; it.p8 }
// RESULT: 10: I

// EXPRESSION: A().let { it.p9 = 9; it.p9 }
// RESULT: 10: I

// EXPRESSION: A().p10
// RESULT: 10: I

// EXPRESSION: A().p11
// RESULT: 11: I

// EXPRESSION: A.op1
// RESULT: 101: I

// EXPRESSION: A.op2
// RESULT: 102: I

// EXPRESSION: A.op3
// RESULT: 103: I

// EXPRESSION: A.op4 = 104; A.op4
// RESULT: 104: I

// EXPRESSION: A.op5 = 105; A.op5
// RESULT: 107: I

// EXPRESSION: A.op6 = 106; A.op6
// RESULT: 107: I

// IGNORE_BACKEND: JVM_IR_WITH_OLD_EVALUATOR
