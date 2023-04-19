// MODULE: common
// FILE: common.kt

expect object Platform {
    fun name1(): String
    fun name2(param: String = "name2"): String
}

// MODULE: jvm(common)
// FILE: jvm.kt


actual object Platform {
    actual fun name1(): String = "name1"
    actual fun name2(param: String) = param
}

fun main() {
    // EXPRESSION: Platform
    // RESULT: instance of Platform(id=ID): LPlatform;
    //Breakpoint!
    val a = 0

    // EXPRESSION: Platform.name1()
    // RESULT: "name1": Ljava/lang/String;
    //Breakpoint!
    val b = 0

    // EXPRESSION: Platform.name2()
    // RESULT: "name2": Ljava/lang/String;
    //Breakpoint!
    val c = 0

    // EXPRESSION: Platform.name2("special")
    // RESULT: "special": Ljava/lang/String;
    //Breakpoint!
    val d = 0

}