class A {
    fun foo(collection: ArrayList<String?>) {
        for (i in collection.indices.reversed()) {
            println(i)
        }
    }
}
