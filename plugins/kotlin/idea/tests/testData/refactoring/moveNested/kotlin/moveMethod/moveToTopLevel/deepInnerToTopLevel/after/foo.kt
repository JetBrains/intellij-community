package bar

fun foo(test: Test.Test2.Test3): Int {
    println(this@Test2)
    return Test.a + test.b
}