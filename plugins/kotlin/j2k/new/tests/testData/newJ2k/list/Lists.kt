// ERROR: Unresolved reference: ELinkedList
class Lists {
    fun test() {
        val xs: MutableList<Any?> = ArrayList()
        val ys: MutableList<Any?> = ELinkedList<Any>()
        val zs = ArrayList<Any?>()
        xs.add(null)
        ys.add(null)
        xs.clear()
        ys.clear()
        zs.add(null)
    }
}