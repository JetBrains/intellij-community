// ERROR: Unresolved reference: asStream
// ERROR: Unresolved reference: asStream
// ERROR: Unresolved reference: asStream
import java.util.stream.Stream

class A {
    fun foo(s: String): Stream<String> {
        // comment

        bar().lineSequence().asStream()
        val lines: Stream<String> = s.lineSequence().asStream()
        return s.lineSequence().asStream()


        // comment
    }

    fun bar(): String {
        return "test"
    }
}
