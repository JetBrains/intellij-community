import java.util.AbstractList

class Foo : AbstractList<String>() {
    override fun get(index: Int): String {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")
}