interface Iface {
    val name: String
    fun foo() = 43
}

fun main() {
    val iface: Iface = object : Iface {
        override val name = "anon"
    }
    //Breakpoint!
    println(iface)
}

// VARIABLE_PATH: iface.name
// EVAL_EXPRESSION: iface.name
