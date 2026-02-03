import java.util.stream.Stream
import kotlin.streams.asStream

class A {
    fun foo(s: String): Stream<String> {
        // comment

        bar().lineSequence().asStream()
        val lines = s.lineSequence().asStream()
        return s.lineSequence().asStream()


        // comment
    }

    fun bar(): String {
        return "test"
    }
}
