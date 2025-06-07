// ERROR: Return type mismatch: expected 'Array<String?>', actual 'Array<String>'.
internal class Test {
    fun someFoo(): Array<String?> {
        return arrayOf<String>("a")
    }
}