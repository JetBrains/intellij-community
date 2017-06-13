package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * @author peter
 */
public interface DataStructure {

  default int drawInt() {
    return drawInt(BoundedIntDistribution.ALL_INTS);
  }
  
  int drawInt(@NotNull IntDistribution distribution);

  @NotNull
  DataStructure subStructure();
  
  <T> T generateNonShrinkable(@NotNull Generator<T> generator);

  <T> T generateConditional(@NotNull Generator<T> generator, @NotNull Predicate<T> condition);
}
