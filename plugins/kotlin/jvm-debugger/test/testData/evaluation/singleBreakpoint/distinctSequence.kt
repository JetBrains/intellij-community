package distinctSequence

fun main(args: Array<String>) {
    fun str(chr: Char): String = String(charArrayOf(chr))
    //Breakpoint!
    listOf(str('a'), str('b'), str('a'), str('c'), str('b')).asSequence().distinct().count()
}

// Muted on JVM_IR_WITH_OLD_EVALUATOR:
// The old evaluator is incompatible with the IR compilation scheme for local functions.