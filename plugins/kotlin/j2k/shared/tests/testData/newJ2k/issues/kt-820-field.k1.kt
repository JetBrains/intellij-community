package demo

import demo.One

internal class Container {
    var myInt: Int = 1
}

internal object One {
    var myContainer: Container = Container()
}

internal class Test {
    var b: Byte = One.myContainer.myInt.toByte()
}
