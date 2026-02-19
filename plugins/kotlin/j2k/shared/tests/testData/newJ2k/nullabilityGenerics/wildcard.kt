internal class TestWildcard {
    var extendsNumber: ArrayList<out Number> = ArrayList()
    var superNumber: ArrayList<in Number> = ArrayList()

    fun test() {
        for (n in extendsNumber) {
            println(n.hashCode())
        }
        for (o in superNumber) {
            println(o.hashCode())
        }
    }
}
