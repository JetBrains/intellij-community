// MODULE: common
// FILE: common.kt
// PLATFORM: common
expect fun debugMe(i: Int): String
fun commonContext(){
    //Breakpoint1
    val str = "Stop here"
}

// ADDITIONAL_BREAKPOINT: common.kt / Breakpoint1

// EXPRESSION: debugMe(42)
// RESULT: "JVM 42": Ljava/lang/String;

// MODULE: jvm(intermediateJvm)
// FILE: jvm.kt

actual fun debugMe(i: Int): String {
    return "JVM $i"
}
fun main(){
    commonContext()
}

// MODULE: intermediateJvm(common)
// FILE: intermediate.kt
// PLATFORM: jvm
