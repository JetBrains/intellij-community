// ERROR: Initializer type mismatch: expected 'kotlin.Array<kotlin.String>', actual 'kotlin.Array<kotlin.String?>'.
// ERROR: Type mismatch: inferred type is 'kotlin.Array<T? (of fun <T> arrayOfNulls)>', but 'kotlin.Array<kotlin.String>' was expected.
internal class ArrayField {
    fun test() {
        val array: Array<String> = arrayOfNulls<String>(0)

        for (s in array) {
            println(s.length)
        }
    }
}
