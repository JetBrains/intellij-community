import java.util.LinkedList

class ForEach {
    fun test() {
        val xs = ArrayList<Any>()
        val ys: MutableList<Any> = LinkedList()
        for (x in xs) {
            ys.add(x)
        }
        for (y in ys) {
            xs.add(y)
        }
    }
}
