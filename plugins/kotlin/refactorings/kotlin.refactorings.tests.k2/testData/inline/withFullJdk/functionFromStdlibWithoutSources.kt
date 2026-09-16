// ERROR: Cannot inline 'println(Int) of kotlin.io' from a decompiled file
// ERROR_K2: Cannot inline 'println(...)' from a decompiled file
// RUNTIME_WITHOUT_SOURCES
fun test() {
    printl<caret>n(42)
}