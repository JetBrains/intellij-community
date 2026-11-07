// ERROR: Cannot check for instance of erased type 'Array<String>'.
// ERROR: Cannot check for instance of erased type 'Array<String>'.
class J {
    fun testObject(obj: Any?): Boolean {
        return obj is Array<String>
    }

    fun testArray(array: Array<Any?>?): Boolean {
        return array is Array<String>
    }
}
