// ERROR: RETURN_TYPE_MISMATCH
class J(x: Int, y: Int) {
    fun foo() {
        return J(0, TODO("Cannot convert element"))
    }
}
