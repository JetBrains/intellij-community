// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class Receiver

class MyContext

context(_: MyContext)
fun Receiver.doA() {
    val <caret>b = getB()
    b?.let { println(it) }
}

fun Receiver.getB(): Int? = TODO()