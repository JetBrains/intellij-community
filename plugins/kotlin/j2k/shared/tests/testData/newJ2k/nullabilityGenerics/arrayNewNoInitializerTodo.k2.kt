// ERROR: Initializer type mismatch: expected 'Array<String>', actual 'Array<String?>'.
// ERROR: Type mismatch: inferred type is 'Array<String?>', but 'Array<String>' was expected.
internal class ArrayField {
    fun test() {
        val array: Array<String> = arrayOfNulls<String>(0)

        for (s in array) {
            println(s.length)
        }
    }
}
