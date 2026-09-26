// ENABLED_LANGUAGE_FEATURE: ContextParameters

context(s: String)
val Clazz.someVal: String
    get() = x + s + " World"

class Clazz {
    val x = "Clazz, "
}

fun main() {
    val clazz = Clazz()
    with("Hello") {
        //Breakpoint!
        println()
    }
}

// EXPRESSION: clazz.someVal
// RESULT: "Clazz, Hello World": Ljava/lang/String;
