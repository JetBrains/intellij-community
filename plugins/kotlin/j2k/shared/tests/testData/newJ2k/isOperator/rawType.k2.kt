internal class C {
    fun bar(o: Any?, collection: Collection<String?>?): Boolean {
        return o is MutableCollection<*> && collection is MutableList<*>
    }
}
