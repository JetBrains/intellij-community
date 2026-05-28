package annotationValue

annotation class Anno(val value: String)

@Anno("abc")
class SomeClass

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 5
}

// EXPRESSION: SomeClass::class.java.getAnnotation(Anno::class.java).value
// RESULT: "abc": Ljava/lang/String;