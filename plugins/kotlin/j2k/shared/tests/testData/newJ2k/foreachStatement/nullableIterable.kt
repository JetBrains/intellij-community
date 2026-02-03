internal class A {
    var list: ArrayList<String?>? = null

    fun foo() {
        for (e in list!!) {
            println(e)
        }
    }
}
