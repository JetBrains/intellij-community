package privateAnnotationCompanionValue

annotation class Anno(val value: String) {
    companion object {
        private val test: Int = 4
    }
}

@Anno("abc")
class SomeClass

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 5
}


// EXPRESSION: Anno.test
// RESULT: 4: I
