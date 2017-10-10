package misc;

import one.util.streamex.StreamEx;

import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class Parallel {
  public static void main(String[] args) {
    // Breakpoint!
    final long count = StreamEx.of(1, 2, 3).parallel((ForkJoinPool) Executors.newWorkStealingPool()).count();
    System.out.println(count);
  }
}
