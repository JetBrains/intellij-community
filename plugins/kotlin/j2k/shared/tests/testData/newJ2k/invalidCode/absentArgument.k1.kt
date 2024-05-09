// ERROR: Type mismatch: inferred type is J but Unit was expected
class J(x: Int, y: Int) {
    fun foo() {
        return J(0, TODO("Cannot convert element"))
    }
}
