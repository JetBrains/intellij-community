package slowCheck;

import java.util.function.Supplier;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class PropertyFalsified extends RuntimeException {
  private final PropertyFailure<?> failure;
  private final Supplier<DataStructure> data;

  public PropertyFalsified(int seed, PropertyFailure<?> failure, Supplier<DataStructure> data) {
    super("Falsified on " + failure.getMinimalCounterexample().getExampleValue() + "\n" +
          "Minimized in " + failure.getTotalMinimizationStepCount() + " steps\n" +
          "Seed=" + seed, failure.getMinimalCounterexample().getExceptionCause());
    this.failure = failure;
    this.data = data;
  }

  public PropertyFailure<?> getFailure() {
    return failure;
  }

  public Object getBreakingValue() {
    return failure.getMinimalCounterexample().getExampleValue();
  }

  public DataStructure getData() {
    return data.get();
  }
}
