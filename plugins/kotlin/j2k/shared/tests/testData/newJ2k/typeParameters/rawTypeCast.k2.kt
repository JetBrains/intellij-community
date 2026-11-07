// ERROR: Return type mismatch: expected 'MutableMap<String?, String?>', actual 'HashMap<Any?, Any?>'.
import java.util.Properties

internal object A {
    fun foo(): MutableMap<String?, String?> {
        val props = Properties()
        return HashMap<Any?, Any?>(props as MutableMap<*, *>)
    }
}
