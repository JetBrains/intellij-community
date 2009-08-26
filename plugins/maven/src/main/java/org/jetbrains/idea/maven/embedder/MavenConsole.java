package org.jetbrains.idea.maven.embedder;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashMap;
import org.apache.maven.plugin.AbstractMojoExecutionException;

import java.text.MessageFormat;
import java.util.Map;

public abstract class MavenConsole {
  public static final int LEVEL_AUTO = -1;
  public static final int LEVEL_DEBUG = 0;
  public static final int LEVEL_INFO = 1;
  public static final int LEVEL_WARN = 2;
  public static final int LEVEL_ERROR = 3;
  public static final int LEVEL_FATAL = 4;
  public static final int LEVEL_DISABLED = 5;
  public static final int LEVEL_ALWAYS = 6;

  public static final String LINE_SEPARATOR = System.getProperty("line.separator");

  public enum OutputType {
    NORMAL, SYSTEM, ERROR
  }

  private final int myOutputLevel;
  private final boolean myPrintStrackTrace;
  private boolean isFinished;

  private static final Map<String, Integer> PREFIX_TO_LEVEL = new THashMap<String, Integer>();
  private static final Map<Integer, String> LEVEL_TO_PREFIX = new THashMap<Integer, String>();

  static {
    map("DEBUG", LEVEL_DEBUG);
    map("INFO", LEVEL_INFO);
    map("WARNING", LEVEL_WARN);
    map("ERROR", LEVEL_ERROR);
    map("FATAL_ERROR", LEVEL_FATAL);
  }

  private static void map(String prefix, int level) {
    PREFIX_TO_LEVEL.put(prefix, level);
    LEVEL_TO_PREFIX.put(level, prefix);
  }

  public MavenConsole(MavenExecutionOptions.LoggingLevel outputLevel, boolean printStrackTrace) {
    myOutputLevel = outputLevel.getLevel();
    myPrintStrackTrace = printStrackTrace;
  }

  public boolean isSuppressed(int level) {
    return level < myOutputLevel;
  }

  public boolean isSuppressed(String line) {
    return isSuppressed(getLevel(line));
  }

  public abstract boolean canPause();

  public abstract boolean isOutputPaused();

  public abstract void setOutputPaused(boolean outputPaused);

  public boolean isFinished() {
    return isFinished;
  }

  public void finish() {
    isFinished = true;
  }

  public abstract void attachToProcess(ProcessHandler processHandler);

  public void systemMessage(int level, String string, Throwable throwable) {
    printMessage(level, string, throwable);
  }

  public void printMessage(int level, String string, Throwable throwable) {
    if (isSuppressed(level)) return;

    OutputType type = OutputType.NORMAL;
    if (throwable != null || level == LEVEL_WARN || level == LEVEL_ERROR || level == LEVEL_FATAL) {
      type = OutputType.ERROR;
    }

    doPrint(composeLine(level, string), type);

    if (level == LEVEL_FATAL) {
      setOutputPaused(false);
    }

    if (throwable != null) {
      String message = null;

      Throwable temp = throwable;
      while (temp != null) {
        if (temp instanceof AbstractMojoExecutionException) {
          message = appendExecutionFailureMessage(message, temp.getMessage());
          message = appendExecutionFailureMessage(message, ((AbstractMojoExecutionException)temp).getLongMessage());

          if (temp.getCause() != null) {
            message = appendExecutionFailureMessage(message, temp.getCause().getMessage());
          }
          break;
        }
        temp = temp.getCause();
      }

      if (message == null) message = throwable.getMessage();

      if (message != null) {
        message += LINE_SEPARATOR;
        doPrint(LINE_SEPARATOR + composeLine(LEVEL_ERROR, message), type);
      }

      if (myPrintStrackTrace) {
        doPrint(LINE_SEPARATOR + StringUtil.getThrowableText(throwable), OutputType.ERROR);
        }
      else {
        doPrint(LINE_SEPARATOR +
                "To view full stack traces, please go to the Settings->Maven and check the 'Print Exception Stack Traces' box." +
                LINE_SEPARATOR,
                type);
      }
    }
  }

  private String appendExecutionFailureMessage(String message, String newMessage) {
    if (message == null) return newMessage;
    if (newMessage == null) return message;
    return message + LINE_SEPARATOR + LINE_SEPARATOR + newMessage;
  }

  protected abstract void doPrint(String text, OutputType type);

  private static int getLevel(String line) {
    return getLevelByPrefix(extractPrefix(line));
  }

  private static String extractPrefix(String line) {
    if (line.startsWith("[")) {
      int closing = line.indexOf("] ", 1);
      if (closing > 1) {
        return line.substring(1, closing);
      }
    }
    return "";
  }

  private static int getLevelByPrefix(String prefix) {
    Integer level = PREFIX_TO_LEVEL.get(prefix);
    return level != null ? level : LEVEL_ALWAYS;
  }

  private static String composeLine(int level, String message) {
    return MessageFormat.format("[{0}] {1}", getPrefixByLevel(level), message);
  }

  private static String getPrefixByLevel(int level) {
    return LEVEL_TO_PREFIX.get(level);
  }
}
