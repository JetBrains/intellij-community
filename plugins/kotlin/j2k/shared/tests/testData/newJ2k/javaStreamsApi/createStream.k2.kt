import java.util.Arrays
import java.util.stream.Collectors
import java.util.stream.Stream

internal class Test {
    fun main(lst: List<String?>) {
        val streamOfList = lst.stream()
            .map<String?> { x: String? -> x + "e" }
            .collect(Collectors.toList())

        val streamOfElements = Stream.of<Int>(1, 2, 3)
            .map<Int?> { x: Int? -> x!! + 1 }
            .collect(Collectors.toList())

        val array = arrayOf<Int>(1, 2, 3)
        val streamOfArray = Arrays.stream<Int>(array)
            .map<Int?> { x: Int? -> x!! + 1 }
            .collect(Collectors.toList())

        val streamOfArray2 = Stream.of<Int>(*array)
            .map<Int?> { x: Int? -> x!! + 1 }
            .collect(Collectors.toList())

        val streamIterate = Stream.iterate<Int>(2) { v: Int -> v * 2 }
            .map<Int?> { x: Int? -> x!! + 1 }
            .collect(Collectors.toList())

        val streamGenerate = Stream.generate<Int?> { 42 }
            .map<Int?> { x: Int? -> x!! + 1 }
            .collect(Collectors.toList())
    }
}
