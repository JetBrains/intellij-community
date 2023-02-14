package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Key;

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
}
