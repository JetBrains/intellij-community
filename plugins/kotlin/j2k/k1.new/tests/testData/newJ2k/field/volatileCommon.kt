import kotlin.concurrent.Volatile

// API_VERSION: 1.9
// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
internal class A {
    @Volatile
    var field1: Int = 0
}
