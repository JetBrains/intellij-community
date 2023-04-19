// MODULE: common
// FILE: common.kt

expect interface Platform {
    open fun name1(): String
    open fun name2(param: String = "name2"): String
}

// MODULE: jvm(common)
// FILE: jvm.kt


actual interface Platform {
    actual fun name1() = "name1"
    actual fun name2(param: String): String = param
}

class TestPlatform() : Platform


fun main() {
    // EXPRESSION: TestPlatform()
    // RESULT: instance of TestPlatform(id=ID): LTestPlatform;
    //Breakpoint!
    val a = 0

    // EXPRESSION: TestPlatform().name1()
    // RESULT: "name1": Ljava/lang/String;
    //Breakpoint!
    val b = 0

    // EXPRESSION: TestPlatform().name2()
    // RESULT: "name2": Ljava/lang/String;
    //Breakpoint!
    val c = 0

    // EXPRESSION: TestPlatform().name2("special")
    // RESULT: "special": Ljava/lang/String;
    //Breakpoint!
    val d = 0

}