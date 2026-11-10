internal interface Handler {
    fun handle(items: MutableList<String?>)

    fun log(items: List<String?>)
}

internal class Adding : Handler {
    override fun handle(items: MutableList<String?>) {
        items.add("a")
    }

    override fun log(items: List<String?>) {
        println(items.size)
    }
}

internal class Reading : Handler {
    override fun handle(items: MutableList<String?>) {
        println(items.size)
    }

    override fun log(items: List<String?>) {
        println(items.size)
    }
}

internal abstract class Base {
    abstract fun names(): List<String>
}

internal class Derived : Base() {
    override fun names(): MutableList<String> {
        return ArrayList<String>()
    }

    fun use() {
        names().add("a")
    }
}
