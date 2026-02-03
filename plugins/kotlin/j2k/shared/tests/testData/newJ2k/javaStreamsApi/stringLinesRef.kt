import java.util.concurrent.Callable
import java.util.stream.Stream
import kotlin.streams.asStream

class A {
    fun foo(ref: Callable<Stream<String>>?) {}

    fun bar() {
        val s = "test"
        foo { s.lineSequence().asStream() }
        foo { "test".lineSequence().asStream() }
    }
}
