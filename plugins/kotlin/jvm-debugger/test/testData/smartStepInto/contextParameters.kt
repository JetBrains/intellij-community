// ENABLED_LANGUAGE_FEATURE: ContextParameters

private class Context1
private class Context2

fun test() {
    context(Context1(), Context2()) {
        <caret>runWithContext(42) { target(it) }
    }
}

context(context1: Context1, context2: Context2)
private fun runWithContext(value: Int, block: (Int) -> Unit) {
    block(value)
}

private fun target(value: Int) {}

// EXISTS: context(context1@Context1\, context2@Context2) runWithContext(Int\, (Int) -> Unit), runWithContext: block.invoke()
