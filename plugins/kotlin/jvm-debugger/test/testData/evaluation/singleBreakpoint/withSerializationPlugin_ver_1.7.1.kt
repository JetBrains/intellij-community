// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1)
// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.1)
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