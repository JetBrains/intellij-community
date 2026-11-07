import java.util.concurrent.Callable
import java.util.stream.Stream
import kotlin.streams.asStream

class A {
    fun foo(ref: Callable<Stream<String?>?>) {}

    fun bar() {
        val s = "test"
        foo(Callable { s.lineSequence().asStream() })
        foo(Callable { "test".lineSequence().asStream() })
    }
}
