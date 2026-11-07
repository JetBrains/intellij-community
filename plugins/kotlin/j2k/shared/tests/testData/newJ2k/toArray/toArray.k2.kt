// ERROR: Initializer type mismatch: expected 'Array<Any?>', actual 'Array<String?>'.
class Foo {
    fun test() {
        val list = mutableListOf<String>("a", "b")
        val array1: Array<Any?> = list.toTypedArray()
        val array2: Array<Any?> = list.toTypedArray<String?>()
    }
}
