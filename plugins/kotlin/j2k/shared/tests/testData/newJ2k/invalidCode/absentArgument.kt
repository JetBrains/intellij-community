// ERROR: Return type mismatch: expected 'Unit', actual 'J'.
class J(x: Int, y: Int) {
    fun foo() {
        return J(0, TODO("Cannot convert element"))
    }
}
