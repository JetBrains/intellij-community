// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-serialization-core-1.3.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-serialization-core-jvm-1.3.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-serialization-json-jvm-1.6.1.jar)
package simpleSerializable

import kotlinx.serialization.Serializable

@Serializable
data class S(val str: String)

fun main() {
    val s = S("Hello!")
    //Breakpoint!
    println(s.str)
}

// EXPRESSION: s.str
// RESULT: "Hello!": Ljava/lang/String;