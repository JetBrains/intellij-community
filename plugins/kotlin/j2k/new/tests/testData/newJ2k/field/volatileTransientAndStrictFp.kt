// ERROR: This annotation is not applicable to target 'member property with backing field'
import kotlin.concurrent.Volatile

internal class A {
    @Deprecated("")
    @Volatile
    var field1: Int = 0

    @Transient
    var field2: Int = 1

    // Should work even for bad modifiers
    @Strictfp
    var field3: Double = 2.0
}
