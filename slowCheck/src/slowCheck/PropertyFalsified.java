package slowCheck;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author peter
 */
@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
public class PropertyFalsified extends RuntimeException {
  static final String FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION = "!!! FAILURE REASON HAS CHANGED DURING MINIMIZATION !!!";
  private final long seed;
  private final PropertyFailure<?> failure;
  private final Supplier<DataStructure> data;

  PropertyFalsified(long seed, PropertyFailure<?> failure, Supplier<DataStructure> data) {
    super(failure.getMinimalCounterexample().getExceptionCause());
    this.seed = seed;
    this.failure = failure;
    this.data = data;
  }

  @Override
  public String getMessage() {
    int minimizationStepCount = failure.getTotalMinimizationStepCount();
    String msg = "Falsified on " + failure.getMinimalCounterexample().getExampleValue() + "\n" +
               (minimizationStepCount > 0 ? "Minimized in " + minimizationStepCount + " steps\n" : "") +
               "Seed=" + seed;
    Throwable first = failure.getFirstCounterExample().getExceptionCause();
    if (exceptionsDiffer(first, failure.getMinimalCounterexample().getExceptionCause())) {
      msg += "\n " + FAILURE_REASON_HAS_CHANGED_DURING_MINIMIZATION;
      if (first != null) {
        msg += "\n Initial exception: " + printStackTrace(first) + "\n==========================\n";
      } else {
        msg += "\n Initially property was falsified without exceptions\n";
      }
    }
    return msg;
  }

  private static boolean exceptionsDiffer(Throwable e1, Throwable e2) {
    if (e1 == null && e2 == null) return false;
    if ((e1 == null) != (e2 == null)) return true;
    if (!e1.getClass().equals(e2.getClass())) return true;

    return !getUserTrace(e1).equals(getUserTrace(e2));
  }

  private static List<String> getUserTrace(Throwable e) {
    List<String> result = new ArrayList<>();
    for (StackTraceElement element : e.getStackTrace()) {
      String s = element.toString();
      if (s.startsWith("slowCheck.CounterExampleImpl.checkProperty")) {
        break;
      }
      result.add(s);
    }
    return result;
  }
  
  private static String printStackTrace(Throwable e) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    e.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
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
