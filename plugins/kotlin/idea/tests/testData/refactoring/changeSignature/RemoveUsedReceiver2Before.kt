open class Container() {
    val contents = arrayListOf("Stuff")
}

fun Container.fi<caret>ll(els: ArrayList<String>) {
    els.forEach {
        this.contents.add(it)
        println("Added `$it` element")
    }
}