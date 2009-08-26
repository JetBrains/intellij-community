package org.jetbrains.idea.maven.embedder;

import org.jetbrains.idea.maven.runner.RunnerBundle;

import java.util.List;

public class MavenConsoleHelper {
  public static void printException(MavenConsole console, Throwable throwable) {
    console.systemMessage(MavenConsole.LEVEL_ERROR, RunnerBundle.message("embedded.build.failed"), throwable);
  }

  public static void printExecutionExceptions(MavenConsole console, MavenExecutionResult result) {
    for (Exception each : (List<Exception>)result.getExceptions()) {
      Throwable cause = each.getCause();
      printException(console, cause == null ? each : cause);
    }
  }
}
