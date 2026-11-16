// ERROR: RETURN_TYPE_MISMATCH
import java.util.Properties

internal object A {
    fun foo(): MutableMap<String?, String?> {
        val props = Properties()
        return HashMap<Any?, Any?>(props as MutableMap<*, *>)
    }
}
