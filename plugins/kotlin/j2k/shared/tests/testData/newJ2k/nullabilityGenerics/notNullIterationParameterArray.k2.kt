// ERROR: Initializer type mismatch: expected 'Array<String?>', actual 'Array<String>'.
class C {
    var stringsField: Array<String> = arrayOf<String>("Hello", "World")

    fun field() {
        for (s in stringsField) {
            println(s.length)
        }
    }

    fun param(strings: Array<String>) {
        for (s in strings) {
            println(s.length)
        }
    }

    fun local() {
        val stringsLocal: Array<String?> = arrayOf<String>("Hello", "World")
        for (s in stringsLocal) {
            println(s!!.length)
        }
    }
}
