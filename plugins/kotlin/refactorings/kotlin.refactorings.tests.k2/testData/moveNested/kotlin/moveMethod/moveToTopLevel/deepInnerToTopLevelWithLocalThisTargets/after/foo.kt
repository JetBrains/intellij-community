package bar

fun Test.Test2.foo(test3: Test.Test2.Test3): Int {
    println(this)
    with(Test.Test2()) {
        println(this)
    }
    return a + test3.b
}