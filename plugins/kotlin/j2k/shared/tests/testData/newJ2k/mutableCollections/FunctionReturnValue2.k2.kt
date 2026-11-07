internal class A {
    val collection: MutableCollection<String>

    init {
        collection = createCollection()
    }

    fun createCollection(): MutableCollection<String> {
        return ArrayList<String>()
    }

    fun foo() {
        collection.add("1")
    }
}
