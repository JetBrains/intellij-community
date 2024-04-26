// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2)

package idea300605

fun test1() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    println()
}

class Foo(val d: Double) {
    fun bar() = println(d)
}

fun test2() {
    val foo = Foo(2.7)
    // STEP_INTO: 2
    // RESUME: 1
    //Breakpoint!
    foo.bar()
    println()
}

fun test3() {
    val lv = "local val is important here"
    fun localFun() {
        println("localFun() + $lv")
    }

    localFun()
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    val newName = ::localFun
    newName()
}

fun test4() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    listOf<Int>().extGenericFun()
}

fun <T> List<T>.extGenericFun(): List<T> = this


fun main() {
    test1()
    test2()
    test3()
    test4()
}
