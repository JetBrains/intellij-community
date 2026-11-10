package test

open class Base {
    open fun handle(items: MutableList<String?>) {
        println(items.size)
    }
}
