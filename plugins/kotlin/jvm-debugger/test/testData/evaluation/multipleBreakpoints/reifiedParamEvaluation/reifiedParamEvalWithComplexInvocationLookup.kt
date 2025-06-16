// IGNORE_K1

fun someFun() = ""

inline fun someInlineFun() = ""

inline fun <reified T11, reified T12> bar(x: String): String {
    if (x == "foo<Int>_bar<Int>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Int_kotlin.Int": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Int>_bar<String>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Int_kotlin.String": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Int>_bar<Any>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Int_kotlin.Any": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Double>_bar<Int>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Double_kotlin.Int": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Double>_bar<String>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Double_kotlin.String": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Double>_bar<Any>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Double_kotlin.Any": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<String>_bar<Int>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.String_kotlin.Int": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<String>_bar<String>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.String_kotlin.String": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<String>_bar<Any>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.String_kotlin.Any": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Any>_bar<Int>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Any_kotlin.Int": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Any>_bar<String>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Any_kotlin.String": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    if (x == "foo<Any>_bar<Any>") {
        // EXPRESSION: T11::class.qualifiedName + "_" + T12::class.qualifiedName
        // RESULT: "kotlin.Any_kotlin.Any": Ljava/lang/String;
        //Breakpoint!
        val y = 1
    }
    return ""
}

inline fun <reified T2> foo(x: String): String {
    return someFun() + someInlineFun() + bar<T2, Int>(x + "_bar<Int>") + someFun() + someInlineFun() + bar<T2, String>(x + "_bar<String>") + someFun() + someInlineFun() + bar<T2, Any>(x + "_bar<Any>") + someFun() + someInlineFun()
}

fun main() {
    someFun() + someInlineFun() + foo<Int>("foo<Int>") + someFun() + someInlineFun() + foo<String>("foo<String>" + foo<Double>("foo<Double>")) + someFun() + someInlineFun() + foo<Any>("foo<Any>") + someFun() + someInlineFun()
}