import java.util.stream.Collectors
import java.util.stream.Stream

internal class Test {
    fun main(lst: MutableList<String?>?) {
        val stream = Stream.of<Int>(1)
        val list = stream.collect(Collectors.toList())
    }
}
