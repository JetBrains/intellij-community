import java.util.stream.DoubleStream;

public class SortedSignedDoubleZeros {
  public static void main(String[] args) {
    // Breakpoint!
    double sum = DoubleStream.of(0., -0.).sorted().sum();
  }
}
