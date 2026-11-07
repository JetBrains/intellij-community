import java.util.stream.Collectors

internal class JavaCode {
    fun toJSON(collection: MutableCollection<Int?>): String {
        return "[" + collection.stream().map<String?> { obj: Int? -> obj.toString() }
            .collect(Collectors.joining(", ")) + "]"
    }
}
