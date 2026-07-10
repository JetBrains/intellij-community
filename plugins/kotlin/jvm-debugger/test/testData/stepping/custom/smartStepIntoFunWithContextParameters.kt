// ENABLED_LANGUAGE_FEATURE: ContextParameters
package smartStepIntoFunWithContextParameters

private class Context1(val id: Int)
private class Context2(val id: Int)

private object Container {
    context(context1: Context1, context2: Context2)
    fun caller(value: Int) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        target(value)
    }

    context(context1: Context1, context2: Context2)
    private fun target(value: Int) {
        println(context1.id + context2.id + value)
    }
}

fun main() {
    context(Context1(1), Context2(2)) {
        Container.caller(3)
    }
}

// IGNORE_OLD_BACKEND
// TODO: remove after KT-87335 is fixed
// IGNORE_K2
