// IGNORE_K1

inline fun <reified T11, reified T12, reified T13, reified T14> foo1() {
    //Breakpoint!
    val x = 1
}

inline fun <reified T21, reified T22> foo2() {
    foo1<Int, T21, Array<T21>, Array<Array<T22>>>()
}

inline fun <reified T31> foo3(x : T31) {
    foo2<T31, Array<T31>>()
}

fun main() {
    foo3("")
}


// EXPRESSION: T11::class.qualifiedName
// RESULT: "kotlin.Int": Ljava/lang/String;

// EXPRESSION: T12::class.qualifiedName
// RESULT: "kotlin.String": Ljava/lang/String;

// EXPRESSION: T13::class.toString()
// RESULT: "class [Ljava.lang.String; (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: T14::class.toString()
// RESULT: "class [[[Ljava.lang.String; (Kotlin reflection is not available)": Ljava/lang/String;


// EXPRESSION: kotlin.reflect.typeOf<T11>().toString()
// RESULT: "int (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: kotlin.reflect.typeOf<T12>().toString()
// RESULT: "java.lang.String (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: kotlin.reflect.typeOf<T13>().toString()
// RESULT: "kotlin.Array<java.lang.String> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: kotlin.reflect.typeOf<T14>().toString()
// RESULT: "kotlin.Array<kotlin.Array<kotlin.Array<java.lang.String>>> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: kotlin.reflect.typeOf<Array<Set<T12>>>().toString()
// RESULT: "kotlin.Array<java.util.Set<java.lang.String>> (Kotlin reflection is not available)": Ljava/lang/String;

// EXPRESSION: (5 as T11).toString()
// RESULT: "5": Ljava/lang/String;

// EXPRESSION: (Any() as T11).toString()
// RESULT: javalangClassCastExceptionjavalangObjectcannotbecasttojavalangInteger

// EXPRESSION: (5 as? T11).toString()
// RESULT: "5": Ljava/lang/String;

// EXPRESSION: Any() as? T11
// RESULT: null: Lnull;

// EXPRESSION: 5 is T11
// RESULT: 1: Z

// EXPRESSION: Any() is T11
// RESULT: 0: Z