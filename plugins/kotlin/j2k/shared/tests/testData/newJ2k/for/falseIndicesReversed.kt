class A {
    fun foo(collection: ArrayList<String?>) {
        for (i in collection.size downTo 0) {
            println(i)
        }
    }
}
