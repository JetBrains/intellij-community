package misc;

import one.util.streamex.StreamEx;

import java.util.stream.Stream;

public class ZipWithGreater {
  public static void main(String[] args) {
    // Breakpoint!
    StreamEx.of(1, 2, 3).zipWith(Stream.of(1, 3, 9, 16, 25)).forEach(x -> {});
  }
}
