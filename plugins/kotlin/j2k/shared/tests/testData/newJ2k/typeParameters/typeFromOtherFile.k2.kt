import javaApi.T
import java.util.Collections

internal class A {
    fun foo(t: T): Any {
        return Collections.nCopies<MutableSet<String?>?>(1, t.set)
    }
}
