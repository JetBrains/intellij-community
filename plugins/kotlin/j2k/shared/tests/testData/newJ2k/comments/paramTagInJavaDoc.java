import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// ^^^ multiple explicit imports to test that Import Optimizer works properly

public class Utility {
    /**
     * @param stream
     */
    public static void usage(
            Stream<?> stream,
            DoubleStream doubleStream,
            IntStream intStream,
            BaseStream<?, ?> baseStream,
            StreamSupport streamSupport
    ) {}
}