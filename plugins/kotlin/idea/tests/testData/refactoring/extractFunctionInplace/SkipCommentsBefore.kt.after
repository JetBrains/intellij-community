fun testFun(x: Any): Unit {
    extracted<caret>(x)
}

private fun extracted(x: Any) {
    //comment
    if (x is String) {
        println("that's String")
        return
    }

    println("that's something else")
}