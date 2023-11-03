// ATTACH_LIBRARY: contexts
// ENABLED_LANGUAGE_FEATURE: ContextReceivers

context(String)
val Clazz.someVal: Int
    get() = 111

class Clazz

fun main() {
    val clazz = Clazz()
    with("context") {
        // EXPRESSION: clazz.someVal
        // RESULT: 111: I
        //Breakpoint!
        println()
    }
}