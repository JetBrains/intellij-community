import java.util.stream.Collectors
import java.util.stream.Stream

internal class Test {
    fun main() {
        val activities = Stream.of<String>("12")
            .map<String?> { v: String? -> v + "nya" }
            .filter { v: String? -> v != null }
            .flatMap<String?> { v: String? ->
                Stream.of<String>(v)
                    .flatMap<String?> { s: String? -> Stream.of<String>(s) }
            }.filter { v: String? ->
                val name = v!!.javaClass.getName()
                if (name == "name") {
                    return@filter false
                }
                name != "other_name"
            }
            .collect(Collectors.toList())
    }
}
