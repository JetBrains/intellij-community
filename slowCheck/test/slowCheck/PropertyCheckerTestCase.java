package slowCheck;

import junit.framework.TestCase;

import java.util.function.Predicate;

/**
 * @author peter
 */
abstract class PropertyCheckerTestCase extends TestCase {
  protected static final CheckerSettings ourTestSettings = CheckerSettings.DEFAULT_SETTINGS.withSeed(0);

  protected  <T> PropertyFailure<T> checkFalsified(Generator<T> generator, Predicate<T> predicate, int minimizationSteps) {
    try {
      PropertyChecker.forAll(ourTestSettings, generator, predicate);
      throw new AssertionError("Can't falsify " + getName());
    }
    catch (PropertyFalsified e) {
      //noinspection unchecked
      PropertyFailure<T> failure = (PropertyFailure<T>)e.getFailure();

      System.out.println(" " + getName());
      System.out.println("Value: " + e.getBreakingValue());
      System.out.println("Data: " + e.getData());
      assertEquals(minimizationSteps, failure.getTotalMinimizationExampleCount());
      assertEquals(e.getBreakingValue(), generator.generateUnstructured(e.getData()));

      return failure;
    }
  }

}
