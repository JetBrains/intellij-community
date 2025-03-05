import java.util.stream.BaseStream
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport

// ^^^ multiple explicit imports to test that Import Optimizer works properly
object Utility {
    /**
     * @param stream
     */
    fun usage(
        stream: Stream<*>?,
        doubleStream: DoubleStream?,
        intStream: IntStream?,
        baseStream: BaseStream<*, *>?,
        streamSupport: StreamSupport?
    ) {
    }
}
