import java.util.LinkedList

class Lists {
    fun test() {
        val xs: MutableList<Any?> = ArrayList()
        val ys: MutableList<Any?> = LinkedList()
        val zs = ArrayList<Any?>()
        xs.add(null)
        ys.add(null)
        xs.clear()
        ys.clear()
        zs.add(null)
    }
}
