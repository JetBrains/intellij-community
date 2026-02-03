import java.util.stream.Stream;

public class DistinctEquals {
  public static class Id {
    static Id create() {
      return new Id();
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return true;
    }
  }

  public static void main(String[] args) {
    // Breakpoint!
    final long res = Stream.of(Id.create(), Id.create(), Id.create()).distinct().count();
  }
}
