internal object Library {
    fun call() {}

    fun string(): String {
        return ""
    }
}

internal class User {
    fun main() {
        Library.call()
        Library.string().isEmpty()
    }
}
