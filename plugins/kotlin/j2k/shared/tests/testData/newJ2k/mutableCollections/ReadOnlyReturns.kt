import java.util.LinkedList

internal open class A {
    private val names: MutableList<String?> = ArrayList<String?>()

    fun getNames(): List<String?> {
        return names
    }

    fun add(name: String?) {
        names.add(name)
    }

    open fun created(): List<String?> {
        return ArrayList<String?>()
    }

    open fun changedByCaller(): MutableList<String> {
        return ArrayList<String>()
    }

    fun storedByCaller(): MutableList<String> {
        return ArrayList<String>()
    }

    fun callers() {
        changedByCaller().add("a")
        val list = storedByCaller()
        list.add("a")
    }

    fun publicMethod(): MutableList<String?> {
        return ArrayList<String?>()
    }
}

internal class B : A() {
    override fun changedByCaller(): MutableList<String> {
        return LinkedList<String>()
    }

    override fun created(): List<String?> {
        return LinkedList<String?>()
    }
}
