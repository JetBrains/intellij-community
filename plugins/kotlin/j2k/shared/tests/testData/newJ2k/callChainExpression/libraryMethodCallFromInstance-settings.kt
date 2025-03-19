// !FORCE_NOT_NULL_TYPES: false
// !SPECIFY_LOCAL_VARIABLE_TYPE_BY_DEFAULT: true
internal class Library {
    fun call() {}

    fun string(): String {
        return ""
    }
}

internal class User {
    fun main() {
        val lib: Library = Library()
        lib.call()
        lib.string().isEmpty()

        Library().call()
        Library().string().isEmpty()
    }
}
