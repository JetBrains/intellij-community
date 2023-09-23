package demo

import demo.One

internal class Container {
    var myInt: Int = 1
}

internal object One {
    var myContainer: Container = Container()
}

internal class IntContainer(i: Int)

internal class Test {
    fun putInt(i: Int) {}
    fun test() {
        putInt(One.myContainer.myInt)
        IntContainer(One.myContainer.myInt)
    }
}
