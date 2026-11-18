// SHOW_KOTLIN_VARIABLES

package frameInlineClassSetValueModalProgress

@JvmInline
value class IntHolder(val i: Int)

fun main() {
    val any: Any = IntHolder(1)
    val nullable: IntHolder? = IntHolder(2)
    //Breakpoint!
    println(any.toString() + nullable)
}

// KotlinValueClassToStringWrapper waits for the EDT while the modal progress of Set Value runs.
// SET_VALUE: any = IntHolder(3)
// SET_VALUE: nullable = IntHolder(4)

// PRINT_FRAME

// EXPRESSION: (any as IntHolder).i
// RESULT: 3: I

// EXPRESSION: nullable!!.i
// RESULT: 4: I
