// IGNORE_K1

// Smart-stepping API reports errors when filtering inline getters/setters when
// debugger.kotlin.report.smart.step.into.targets.detection.failure == true
// IGNORE_K2

inline var <reified T, reified R> Map<T, R>.inlineVar1: String
    get() {
        return ""
    }
    set(value) {
        //Breakpoint!
        val x = 1
    }

inline var <reified T, reified R> Map<T, R>.inlineVar2: String
    get() {
        return ""
    }
    set(value) {
        inlineVar1 = ""
    }

inline val <reified T> T.inlineVal1: String
    get() {
        mapOf<T, Int>().inlineVar2 = ""
        return ""
    }

inline val <reified T> T.inlineVal2: String
    get() {
        return inlineVal1
    }

inline fun <reified T> foo1(x: T): String {
    return x.inlineVal2
}


fun main() {
    foo1("")
}

// EXPRESSION: T::class.simpleName + "_" + R::class.simpleName
// RESULT: "String_Int": Ljava/lang/String;
