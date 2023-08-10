// FILE: javaSyntheticPropertyGetter.kt
package javaSyntheticPropertyGetter

fun main(args: Array<String>) {
    val myJavaClass = forTests.MyJavaClass()
    //Breakpoint!
    bar(foo(myJavaClass.foo))
}

fun foo(any: Any) = any
fun bar(any: Any) = any

// SMART_STEP_INTO_BY_INDEX: 3

// FILE: forTests/MyJavaClass.java
package forTests;

public class MyJavaClass {
    public int getFoo() {
        return 1;
    }
}
// IGNORE_K2
