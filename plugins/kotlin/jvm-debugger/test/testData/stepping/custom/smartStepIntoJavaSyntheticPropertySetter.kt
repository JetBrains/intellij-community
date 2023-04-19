// FILE: javaSyntheticPropertyGetter.kt
package javaSyntheticPropertyGetter

fun main(args: Array<String>) {
    val myJavaClass = forTests.MyJavaClass()
    //Breakpoint!
    myJavaClass.foo = foo(bar(1))
}

fun foo(any: Int): Int = any

fun bar(any: Int): Int = any

// SMART_STEP_INTO_BY_INDEX: 1

// FILE: forTests/MyJavaClass.java
package forTests;

public class MyJavaClass {
    private int foo;
    public int getFoo() {
        return 1;
    }
    public void setFoo(int foo) {
        this.foo = foo;
    }
}
// IGNORE_K2
