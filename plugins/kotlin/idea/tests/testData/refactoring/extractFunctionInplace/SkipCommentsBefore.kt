fun testFun(x: Any): Unit {
    <selection>//comment
    if (x is String) {
        println("that's String")
        return
    }

    println("that's something else")</selection>
}