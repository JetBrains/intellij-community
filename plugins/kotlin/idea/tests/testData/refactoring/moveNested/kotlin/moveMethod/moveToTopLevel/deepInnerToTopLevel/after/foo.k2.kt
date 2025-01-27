package bar

fun foo(test3: Test.Test2.Test3): Int {
    println(this@Test2)
    return Test.a + test3.b
}