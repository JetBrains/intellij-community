internal class C {
    fun foo1(collection: MutableCollection<String?>) {
        for (i in collection.indices) {
            print(i)
        }
    }
}
