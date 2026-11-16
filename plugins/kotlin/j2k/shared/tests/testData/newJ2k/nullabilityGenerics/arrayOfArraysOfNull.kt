// ERROR: INITIALIZER_TYPE_MISMATCH
class Test {
    fun foo() {
        val ss: Array<Array<String?>> = Array<Array<String?>?>(5) { arrayOfNulls<String>(5) }
    }
}
