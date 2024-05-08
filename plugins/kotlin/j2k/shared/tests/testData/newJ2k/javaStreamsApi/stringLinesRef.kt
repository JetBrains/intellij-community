// ERROR: Unresolved reference: asStream
// ERROR: Type mismatch: inferred type is Unit! but Stream<String?>! was expected
// ERROR: Unresolved reference: asStream
// ERROR: Type mismatch: inferred type is Unit! but Stream<String?>! was expected
import java.util.concurrent.Callable
import java.util.stream.Stream

class A {
    fun foo(ref: Callable<Stream<String?>>?) {}

    fun bar() {
        val s = "test"
        foo(Callable<Stream<String?>> { s.lineSequence().asStream() })
        foo(Callable<Stream<String?>> { "test".lineSequence().asStream() })
    }
}
