package com.jetbrains.python.console.pydev;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

/**
 * @author traff
 */
public abstract class AbstractConsoleCommunication implements ConsoleCommunication {
  public static final int MAX_ATTEMPTS = 3;
  public static final long TIMEOUT = (long)(10e9);

  protected final Project myProject;
  /**
   * Signals that the next command added should be sent as an input to the server.
   */
  public volatile boolean waitingForInput;


  public AbstractConsoleCommunication(Project project) {
    myProject = project;

  }

  public static Pair<String, Boolean> parseExecResponseString(String str) {
    Boolean more;
    String errorContents = null;
    String lower = str.toLowerCase();
    if (lower.equals("true") || lower.equals("1")) {
      more = true;
    }
    else if (lower.equals("false") || lower.equals("0")) {
      more = false;
    }
    else {
      more = false;
      errorContents = str;
    }
    return new Pair<String, Boolean>(errorContents, more);
  }

  @Override
  public boolean isWaitingForInput() {
    return waitingForInput;
  }
}
