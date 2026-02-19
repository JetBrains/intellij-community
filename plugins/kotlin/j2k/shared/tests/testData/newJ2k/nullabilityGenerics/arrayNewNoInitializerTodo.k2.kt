// ERROR: Initializer type mismatch: expected 'Array<String>', actual 'Array<String?>'.
internal class ArrayField {
    fun test() {
        val array: Array<String> = arrayOfNulls<String>(0)

        for (s in array) {
            println(s.length)
        }
    }
}
