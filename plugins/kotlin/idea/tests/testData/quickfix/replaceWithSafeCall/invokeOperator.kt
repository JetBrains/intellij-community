// "Replace with safe (?.) call" "true"
// WITH_STDLIB

val functions: Map<String, () -> Any> = TODO()

fun run(name: String) = functions[name]<caret>()
/* IGNORE_FIR */