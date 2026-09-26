internal class Library {
    fun call() {}

    fun string(): String {
        return ""
    }
}

internal class User {
    fun main() {
        val lib = Library()
        lib.call()
        lib.string().isEmpty()

        Library().call()
        Library().string().isEmpty()
    }
}
