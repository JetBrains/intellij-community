package com.example

import java.util.function.Function

object ConvertTest {
    var test: Function<CharArray, String>? = null

    init {
        test = Function { chars: CharArray? ->
            String(
                chars!!
            )
        }
    }
}
