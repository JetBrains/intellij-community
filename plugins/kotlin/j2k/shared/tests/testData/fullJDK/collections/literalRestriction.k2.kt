import java.util.Arrays
import java.util.List
import java.util.Set

class Collections {
    fun test() {
        val field = "A"
        val arr = arrayOf<String>("A")
        val x1 = Arrays.asList<String>(field)
        val x2 = Arrays.asList<String>(*arr)
        val y1 = List.of<String>(field)
        val y2 = List.of<String>(*arr)
        val z1 = Set.of<String>(field)
        val z2 = Set.of<String>(*arr)
    }
}
