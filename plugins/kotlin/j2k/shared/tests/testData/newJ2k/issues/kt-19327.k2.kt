import java.util.stream.Collectors

internal class JavaCode {
    fun toJSON(collection: Collection<Int?>): String {
        return "[" + collection.stream().map<String?> { obj: Int? -> obj.toString() }
            .collect(Collectors.joining(", ")) + "]"
    }
}
