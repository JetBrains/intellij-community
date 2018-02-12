import java.util.stream.DoubleStream;

public class PrimitiveResultDouble {
  public static void main(String[] args) {
    // Breakpoint!
    double res = DoubleStream.of(1, 2, 3).sum();
  }
}
