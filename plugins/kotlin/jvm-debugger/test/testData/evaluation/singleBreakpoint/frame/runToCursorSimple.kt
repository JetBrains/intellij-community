// Attack library to test it will not break simple run-to-cursor action (IDEA-351849 regression)
// ATTACH_JAVA_AGENT_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-coroutines-core-jvm-1.7.3.jar)


package runToCursorSimple

fun main() {
    //Breakpoint!
    val a = 1
    val b = 2
    // RUN_TO_CURSOR: 1
    val c = 3
}

// PRINT_FRAME
