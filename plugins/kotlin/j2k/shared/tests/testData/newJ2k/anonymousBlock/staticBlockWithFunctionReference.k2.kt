package com.example

import java.util.function.Function

object ConvertTest {
    var test: Function<CharArray?, String?>

    init {
        test = Function { value: CharArray? -> String(value!!) }
    }
}
