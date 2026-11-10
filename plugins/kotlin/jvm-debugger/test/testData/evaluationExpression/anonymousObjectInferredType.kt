interface Iface {
    val name: String
}

fun main() {
    val obj = object : Iface {
        override val name = "anon"
    }
    //Breakpoint!
    println(obj)
}

// VARIABLE_PATH: obj.name
// EVAL_EXPRESSION: obj.name
