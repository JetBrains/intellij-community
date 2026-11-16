// ERROR: CANNOT_CHECK_FOR_ERASED
// ERROR: CANNOT_CHECK_FOR_ERASED
class J {
    fun testObject(obj: Any?): Boolean {
        return obj is Array<String>
    }

    fun testArray(array: Array<Any?>?): Boolean {
        return array is Array<String>
    }
}
