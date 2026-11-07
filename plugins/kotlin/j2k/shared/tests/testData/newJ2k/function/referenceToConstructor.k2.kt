import java.util.stream.Collectors

object TestLambda {
    @JvmStatic
    fun main(args: Array<String>) {
        val names = mutableListOf<String>("A", "B")
        val people = names.stream().map<Person?> { name: String? -> Person(name) }.collect(Collectors.toList())
    }
}
