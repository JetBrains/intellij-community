import java.util.stream.IntStream;

public class StreamProducerParameter {
  public static void main(String[] args) {
<caret>    IntStream.of(IntStream.of(1).sum()).sum();
  }
}
