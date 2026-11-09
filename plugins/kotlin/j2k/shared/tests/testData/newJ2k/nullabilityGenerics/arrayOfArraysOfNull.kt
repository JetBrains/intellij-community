// ERROR: Initializer type mismatch: expected 'Array<Array<String?>>', actual 'Array<Array<String?>?>'.
class Test {
    fun foo() {
        val ss: Array<Array<String?>> = Array<Array<String?>?>(5) { arrayOfNulls<String>(5) }
    }
}
