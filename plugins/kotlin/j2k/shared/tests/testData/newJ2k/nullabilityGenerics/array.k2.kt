// ERROR: Initializer type mismatch: expected 'Array<String>', actual 'Array<String?>'.
// ERROR: Return type mismatch: expected 'Array<String>', actual 'Array<String?>'.
// TODO support array initializers
internal class ArrayArgument {
    fun test(array: Array<String>) {
        for (s in array) {
            println(s.hashCode())
        }

        takesArray(array)
    }

    private fun takesArray(array: Array<String>?) {
    }
}

internal class ArrayMethodCall {
    fun test() {
        val array = strings()

        for (s in array) {
            println(s.hashCode())
        }
    }

    private fun strings(): Array<String> {
        return strings2()
    }

    private fun strings2(): Array<String> {
        return arrayOfNulls<String>(0)
    }
}

internal class ArrayParameter {
    fun test(param: Array<String>) {
        val array = param

        for (s in array) {
            println(s.hashCode())
        }
    }
}

internal class ArrayField {
    var field: Array<String> = arrayOfNulls<String>(0)

    fun test() {
        val array = field

        for (s in array) {
            println(s.hashCode())
        }
    }
}
