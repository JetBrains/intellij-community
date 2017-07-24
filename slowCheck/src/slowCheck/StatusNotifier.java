package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * @author peter
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
class StatusNotifier {
  private final int iterationCount;
  private int currentIteration;
  private long lastPrinted = System.currentTimeMillis();

  StatusNotifier(int iterationCount) {
    this.iterationCount = iterationCount;
  }

  void iterationStarted(int iteration) {
    currentIteration = iteration;
    if (shouldPrint()) {
      System.out.println(formatCurrentTime() + ": iteration " + currentIteration + " of " + iterationCount + "...");
    }
  }

  void counterExampleFound(Iteration<?> iteration) {
    lastPrinted = System.currentTimeMillis();
    System.err.println(formatCurrentTime() + ": failed on iteration " + currentIteration + " (" + iteration.printSeeds() + "), shrinking...");
  }

  private boolean shouldPrint() {
    if (System.currentTimeMillis() - lastPrinted > 5_000) {
      lastPrinted = System.currentTimeMillis();
      return true;
    }
    return false;
  }

  private int lastReportedStage = -1;
  private String lastReportedTrace = null;
  <T> void shrinkAttempt(PropertyFailure<T> failure, Iteration<T> iteration) {
    if (shouldPrint()) {
      int stage = failure.getMinimizationStageCount();
      System.out.println(formatCurrentTime() + ": still shrinking (" + iteration.printSeeds() + "). " +
                         "Examples tried: " + failure.getTotalMinimizationExampleCount() +
                         ", successful minimizations: " + stage);
      if (lastReportedStage != stage) {
        lastReportedStage = stage;

        System.err.println(" Current minimal example: " + failure.getMinimalCounterexample().getExampleValue());

        Throwable exceptionCause = failure.getMinimalCounterexample().getExceptionCause();
        if (exceptionCause != null) {
          String trace = shortenStackTrace(exceptionCause);
          if (!trace.equals(lastReportedTrace)) {
            lastReportedTrace = trace;
            System.err.println(" Reason: " + trace);
          }
        }
        System.err.println();
      }
    }
  }

  private static String shortenStackTrace(Throwable e) {
    String trace = printStackTrace(e);
    return trace.length() > 1000 ? trace.substring(0, 1000) + "..." : trace;
  }

  @NotNull
  private static String formatCurrentTime() {
    return LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()));
  }

  static String printStackTrace(Throwable e) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    e.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }
}
