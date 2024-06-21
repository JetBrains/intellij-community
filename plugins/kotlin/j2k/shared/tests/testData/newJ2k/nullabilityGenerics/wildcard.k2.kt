internal class TestWildcard {
    var extendsNumber: ArrayList<out Number> = ArrayList<Number>()
    var superNumber: ArrayList<in Number> = ArrayList<Number>()

    fun test() {
        for (n in extendsNumber) {
            println(n.hashCode())
        }
        for (o in superNumber) {
            println(o.hashCode())
        }
    }
}
