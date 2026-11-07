// ERROR: Initializer type mismatch: expected 'Array<Array<Any>>', actual 'Array<Array<Any>?>'.
internal class Test {
    var field: Array<Array<Any>> = arrayOf<Array<Any>?>( // 1st row
        arrayOf<Any>(1, "2", 3.0),  // 2nd row
        arrayOf<Any>( /*large*/10, "20",  /*precise*/3.14)
    )
}
