// ERROR: This annotation is not applicable to target 'member property with backing field'. Applicable targets: function, constructor, getter, setter, class
import kotlin.concurrent.Volatile

internal class A {
    @Deprecated("")
    @Volatile
    var field1: Int = TODO()

    @Transient
    var field2: Int = 1

    // Should work even for bad modifiers
    @Strictfp
    var field3: Double = TODO()
}
