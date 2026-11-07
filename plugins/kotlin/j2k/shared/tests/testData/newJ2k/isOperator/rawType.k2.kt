internal class C {
    fun bar(o: Any?, collection: MutableCollection<String?>?): Boolean {
        return o is MutableCollection<*> && collection is MutableList<*>
    }
}
