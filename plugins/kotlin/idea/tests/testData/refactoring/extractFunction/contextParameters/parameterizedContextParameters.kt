// COMPILER_ARGUMENTS: -Xcontext-parameters
context(ctx: A) fun <A> implicit():A = ctx

interface MyFormatter {
    fun format(input: String): String
}

context(param: String)
fun MyFormatter.problematicFunction(): String {
    <selection>return implicit<MyFormatter>().format("Value: $param")</selection>
}

// IGNORE_K1