import java.util.Set

class Collections {
    fun test() {
        val x = Set.of<String>("A", "A")
        val y = Set.of<String?>("A", null)
        val z = Set.of<String?>(null)
    }
}
