// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface TestMessageFormatter {
    fun format(message: String): String
}

context(ctx: T) fun <T> implicit(): T = ctx

context(<caret>f: TestMessageFormatter)
fun formatWithUnnamedContext(name: String): String {
    return implicit<TestMessageFormatter>().format("Operation: $name")
}

fun main() {
    val formatter = object : TestMessageFormatter {
        override fun format(message: String): String {
            return "[$message]"
        }
    }
    context(formatter) {
        println(formatWithUnnamedContext("test"))
    }
}
