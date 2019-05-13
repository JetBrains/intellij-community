package concatenate;

import one.util.streamex.StreamEx;

import java.util.stream.Stream;

public class PrependMany {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2).prepend(Stream.of(3, 4, 5)).forEach(x -> {});
  }
}
