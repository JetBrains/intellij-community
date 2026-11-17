package castToClassNamedAsJdkClass

interface I

class HashMap<K, V>(val mine: String) : I
class ArrayList(val plain: String) : I

fun main() {
    val i: I = HashMap<String, Int>("my HashMap")
    val j: I = ArrayList("my ArrayList")
    //Breakpoint!
    println()
}

// The star projections follow the project classes, not java.util.HashMap and java.util.ArrayList.
// VARIABLE_PATH: i.mine
// EVAL_EXPRESSION: (i as HashMap<*, *>).mine
// VARIABLE_PATH: j.plain
// EVAL_EXPRESSION: (j as ArrayList).plain
