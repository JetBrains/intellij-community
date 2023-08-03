internal class Test {
    var field: Array<Array<Any>> = arrayOf( // 1st row
        arrayOf(1, "2", 3.0),  // 2nd row
        arrayOf( /*large*/10, "20",  /*precise*/3.14)
    )
}
