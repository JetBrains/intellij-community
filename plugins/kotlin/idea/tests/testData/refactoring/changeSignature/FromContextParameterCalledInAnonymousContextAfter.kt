// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
interface TestMessageFormatter {
    fun format(message: String): String
}

context(ctx: T) fun <T> implicit(): T = ctx

fun formatWithUnnamedContext(f: TestMessageFormatter, name: String): String {
    return with(f) {
        implicit<TestMessageFormatter>()
    }.format("Operation: $name")
}

fun main() {
    val formatter = object : TestMessageFormatter {
        override fun format(message: String): String {
            return "[$message]"
        }
    }
    context(formatter) {
        println(formatWithUnnamedContext(contextOf<TestMessageFormatter>(), "test"))
    }
}
