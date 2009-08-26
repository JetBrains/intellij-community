package org.jetbrains.idea.maven.embedder;

import org.codehaus.plexus.logging.AbstractLoggerManager;
import org.codehaus.plexus.logging.Logger;

public class CustomLoggerManager extends AbstractLoggerManager {
  private final MavenLogger myLogger;



  public CustomLoggerManager(MavenExecutionOptions.LoggingLevel loggingLevel) {
    myLogger = new MavenLogger();
    myLogger.setThreshold(loggingLevel.getLevel());
  }

  public MavenLogger getLogger() {
    return myLogger;
  }

  public void setThreshold(int i) {
  }

  public int getThreshold() {
    return myLogger.getThreshold();
  }

  public void setThresholds(int i) {
  }

  public void setThreshold(String s, String s1, int i) {
  }

  public int getThreshold(String s, String s1) {
    return myLogger.getThreshold();
  }

  public Logger getLoggerForComponent(String s, String s1) {
    return myLogger;
  }

  public void returnComponentLogger(String s, String s1) {
  }

  public int getActiveLoggerCount() {
    return 1;
  }
}
