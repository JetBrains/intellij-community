// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4)

package filterSuspend

suspend fun Int.foo(): Int = 42

suspend fun main() {
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    (42).foo().foo()
}
