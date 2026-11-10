import java.util.Optional
import java.util.function.Consumer

internal class A {
    fun changedThroughArray(lists: Array<MutableList<String>?>) {
        lists[0]!!.add("a")
    }

    fun readThroughArray(lists: Array<MutableList<String?>?>): Int {
        return lists[0]!!.size
    }

    fun changedThroughOptional(optional: Optional<MutableList<String>>) {
        optional.get().add("a")
    }

    fun changedThroughOptionalLambda(optional: Optional<MutableList<String?>>) {
        optional.ifPresent(Consumer { list: MutableList<String?>? -> list!!.add("a") })
    }

    fun readThroughOptional(optional: Optional<MutableList<String?>>): Int {
        return optional.get().size
    }
}
