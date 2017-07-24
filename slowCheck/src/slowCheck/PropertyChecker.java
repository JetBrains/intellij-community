package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * An entry point to property-based testing. The main usage pattern: {@code PropertyChecker.forAll(generator).shouldHold(property)}.
 */
public class PropertyChecker<T> {
  private final Generator<T> generator;
  private long globalSeed = new Random().nextLong();
  private IntUnaryOperator sizeHintFun = iteration -> (iteration - 1) % 100 + 1;
  private int iterationCount = 100;

  private PropertyChecker(Generator<T> generator) {
    this.generator = generator;
  }

  /**
   * Creates a property checker for the given generator. It can be further customized using {@code with*}-methods, 
   * and should finally used for property to check via {@link #shouldHold(Predicate)} call.
   */
  public static <T> PropertyChecker<T> forAll(Generator<T> generator) {
    return new PropertyChecker<>(generator);
  }

  /**
   * This function allows to start the test with a fixed random seed. It's useful to reproduce some previous test run and debug it.
   * @param seed A random seed to use for the first iteration.
   *             The following iterations will use other, pseudo-random seeds, but still derived from this one.
   * @return this PropertyChecker
   */
  public PropertyChecker<T> withSeed(long seed) {
    globalSeed = seed;
    return this;
  }

  /**
   * @param iterationCount the number of iterations to try. By default it's 100.
   * @return this PropertyChecker
   */
  public PropertyChecker<T> withIterationCount(int iterationCount) {
    this.iterationCount = iterationCount;
    return this;
  }

  /**
   * @param sizeHintFun a function determining how size hint should be distributed depending on the iteration number.
   *                    By default the size hint will be 1 in the first iteration, 2 in the second one, and so on until 100,
   *                    then again 1,...,100,1,...,100, etc.
   * @return this PropertyChecker
   * @see DataStructure#getSizeHint() 
   */
  public PropertyChecker<T> withSizeHint(@NotNull IntUnaryOperator sizeHintFun) {
    this.sizeHintFun = sizeHintFun;
    return this;
  }

  /**
   * Checks the property within a single iteration by using specified seed and size hint. Useful to debug the test after it's failed.
   */
  public PropertyChecker<T> rechecking(long seed, int sizeHint) {
    return withSeed(seed).withSizeHint(whatever -> sizeHint).withIterationCount(1);
  }

  /**
   * Checks that the given property returns {@code true} and doesn't throw exceptions by running the generator and the property
   * given number of times (see {@link #withIterationCount(int)}).
   */
  public void shouldHold(@NotNull Predicate<T> property) {
    Iteration<T> iteration = new CheckSession<>(generator, property, globalSeed, iterationCount, sizeHintFun).firstIteration();
    while (iteration != null) {
      iteration = iteration.performIteration();
    }
  }

}

