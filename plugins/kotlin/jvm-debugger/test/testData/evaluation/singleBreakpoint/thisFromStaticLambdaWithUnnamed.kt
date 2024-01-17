// FILE: thisFromStaticLambdaWithUnnamed.kt
package thisFromStaticLambdaWithUnnamed

class ForStaticLambda {
    var x = 10

    fun foo() {
        boo { _, y ->
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

// FILE: thisFromStaticLambdaWithUnnamed/JustInterface.java
package thisFromStaticLambdaWithUnnamed;

public interface JustInterface {
    void someLonelyMethod(String before, int a);
}
