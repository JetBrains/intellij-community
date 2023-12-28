package localVariableWithDelegate

fun main() {
    class Local(val string: String)

    val name by lazy { Local("John") }

    //Breakpoint!
    name
}

// EXPRESSION: name
// RESULT: instance of localVariableWithDelegate.LocalVariableWithDelegateKt$main$Local(id=ID): LlocalVariableWithDelegate/LocalVariableWithDelegateKt$main$Local;

// EXPRESSION: name.string
// RESULT: "John": Ljava/lang/String;
