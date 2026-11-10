fun main() {
    val obj = object {
        val name = "anon"
    }
    //Breakpoint!
    println(obj)
}

// VARIABLE_PATH: obj.name
// EVAL_EXPRESSION: obj.name
