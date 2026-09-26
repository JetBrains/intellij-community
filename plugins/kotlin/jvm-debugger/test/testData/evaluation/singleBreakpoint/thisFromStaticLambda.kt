// FILE: thisFromStaticLambda.kt
package thisFromStaticLambda

class ForStaticLambda {
    var x = 10

    fun foo() {
        boo { p, y ->
            //Breakpoint!
            println(x + y)
        }
    }
}

private fun boo(ha: JustInterface) {
    ha.someLonelyMethod("b", 3)
}

fun main() {
    val example = ForStaticLambda()
    example.foo()
}
// EXPRESSION: x
// RESULT: 10: I

// FILE: thisFromStaticLambda/JustInterface.java
package thisFromStaticLambda;

public interface JustInterface {
    void someLonelyMethod(String before, int a);
}
