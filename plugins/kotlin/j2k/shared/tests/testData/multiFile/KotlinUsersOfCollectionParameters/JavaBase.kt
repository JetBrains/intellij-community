package test

open class JavaBase {
    open fun handle(items: MutableList<String?>) {
        println(items.size)
    }

    fun fill(lists: MutableList<MutableList<String?>?>) {
        lists.add(ArrayList<String?>())
    }
}
