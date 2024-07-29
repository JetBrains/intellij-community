internal class MethodCallArgument {
    fun test() {
        takesStrings(strings(), strings())
    }

    fun strings(): ArrayList<String> {
        return ArrayList()
    }

    private fun takesStrings(strings1: ArrayList<String>, strings2: ArrayList<String>) {
    }
}

internal class MethodCallArgumentReverse {
    fun test() {
        takesStrings(strings1(), strings2())
    }

    fun strings1(): ArrayList<String> {
        return ArrayList()
    }

    fun strings2(): ArrayList<String> {
        return ArrayList()
    }

    private fun takesStrings(strings1: ArrayList<String>, strings2: ArrayList<String>) {
    }
}
