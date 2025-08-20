package coroutinesView

// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps_org_jetbrains_kotlinx_kotlinx_coroutines_core_1_10_2//file:kotlinx_coroutines_core_1_10_2.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps_org_jetbrains_kotlinx_kotlinx_coroutines_core_jvm_1_10_2//file:kotlinx_coroutines_core_jvm_1_10_2.jar)

import kotlinx.coroutines.*

fun main() {
    runBlocking(CoroutineName("root")) {
        println()
        //Breakpoint!
        delay(11L)
        println()
    }
}