package genericCastOnCollectionElement

interface I

class B<T>(val v: T) : I

fun main() {
    val arr: Array<I> = arrayOf(B("x"))
    val list: List<I> = listOf(B("y"))
    //Breakpoint!
    println()
}

// VARIABLE_PATH: arr.0.v
// EVAL_EXPRESSION: (arr[0] as B<*>).v
// VARIABLE_PATH: list.0.v
// EVAL_EXPRESSION: (list.get(0) as B<*>).v
