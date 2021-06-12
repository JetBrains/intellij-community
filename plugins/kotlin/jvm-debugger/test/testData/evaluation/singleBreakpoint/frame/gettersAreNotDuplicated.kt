package gettersAreNotDuplicated

import kotlin.reflect.KProperty

abstract class A {
    open val aInt get() = 30
}

class B : A() {
    override val aInt by MyDelegate()
}

fun main(args: Array<String>) {
    val instance = B()
    //Breakpoint!
    println("")
}

class MyDelegate {
    operator fun getValue(t: Any?, p: KProperty<*>): Int = 1
}

// PRINT_FRAME
// RENDER_DELEGATED_PROPERTIES
