import java.util.stream.Collectors

object TestLambda {
    @JvmStatic
    fun main(args: Array<String>) {
        val names: List<String> = mutableListOf("A", "B")
        val people = names.stream().map { name: String? -> Person(name) }.collect(Collectors.toList())
    }
}
