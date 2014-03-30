package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Key;

/**
 * @author Konstantin Kolosovsky.
 */
public class LineCommandAdapter implements LineCommandListener {

  private boolean myCancelled;

  @Override
  public void cancel() {
    myCancelled = true;
  }

  @Override
  public boolean isCanceled() {
    return myCancelled;
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
  }

  @Override
  public void processTerminated(int exitCode) {
  }

  @Override
  public void startFailed(Throwable exception) {
  }
}
