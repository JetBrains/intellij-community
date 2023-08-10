package smartStepIntoToLambdaParameterAfterSam

import java.util.function.Supplier
fun main(args: Array<String>) {
    getAndProcessString({ "str" },
                        { println(it) })
}

fun getAndProcessString(stringSupplier: Supplier<String>, f: (String) -> Unit) {
    // SMART_STEP_INTO_BY_INDEX: 0
    // RESUME: 1
    //Breakpoint!
    f(stringSupplier.get())
}
// IGNORE_K2
