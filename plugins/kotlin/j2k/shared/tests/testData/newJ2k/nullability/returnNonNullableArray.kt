// ERROR: Return type mismatch: expected 'kotlin.Array<kotlin.String?>', actual 'kotlin.Array<kotlin.String>'.
internal class Test {
    fun someFoo(): Array<String?> {
        return arrayOf<String>("a")
    }
}