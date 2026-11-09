internal class A {
    fun createCollection(): MutableCollection<String> {
        return ArrayList<String>()
    }

    fun foo(): Collection<String> {
        val collection = createCollection()
        collection.add("a")
        return collection
    }
}
