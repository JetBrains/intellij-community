package localClass

fun main() {
    class LocalClass { }

    //Breakpoint!
    val a = 1
}

// EXPRESSION: LocalClass()
// RESULT: instance of localClass.LocalClassKt$main$LocalClass(id=ID): LlocalClass/LocalClassKt$main$LocalClass;


// TODO: Support local classes in the IR Evaluator backend.