class J {
    fun testObject(obj: Any?): Boolean {
        return obj is Array<*> && obj.isArrayOf<String>()
    }

    fun testArray(array: Array<Any?>?): Boolean {
        return array?.isArrayOf<String>() == true
    }
}
