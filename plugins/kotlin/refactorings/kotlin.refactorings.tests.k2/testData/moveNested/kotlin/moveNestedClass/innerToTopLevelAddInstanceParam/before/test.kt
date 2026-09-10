class Test {
    inner class <caret>InnerClass {
        fun fun1() {
            println(test)
        }
    }

    val test = InnerClass()
}

fun main(args: Array<String>) {
    val test = Test()
    val innerClass = test.InnerClass()
    innerClass.fun1()
}

fun Test.foo() {
    InnerClass()
}