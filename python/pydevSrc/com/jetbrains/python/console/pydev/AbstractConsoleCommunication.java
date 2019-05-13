// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.pydev;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.Locale;

/**
 * @author traff
 */
public abstract class AbstractConsoleCommunication implements ConsoleCommunication {
  public static final int MAX_ATTEMPTS = 3;
  public static final long TIMEOUT = (long)(10e9);

  private VirtualFile myConsoleFile;

  protected final Project myProject;
  /**
   * Signals that the next command added should be sent as an input to the server.
   */
  public volatile boolean waitingForInput;

  private final List<ConsoleCommunicationListener> communicationListeners = ContainerUtil.createLockFreeCopyOnWriteList();


  public AbstractConsoleCommunication(Project project) {
    myProject = project;
  }

  public static Pair<String, Boolean> parseExecResponseString(String str) {
    boolean more;
    String errorContents = null;
    String lower = str.toLowerCase(Locale.ENGLISH);
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
    return Pair.create(errorContents, more);
  }

  @Override
  public boolean isWaitingForInput() {
    return waitingForInput;
  }


  @Override
  public void addCommunicationListener(ConsoleCommunicationListener listener) {
    communicationListeners.add(listener);
  }

  @Override
  public void notifyCommandExecuted(boolean more) {
    for (ConsoleCommunicationListener listener: communicationListeners) {
      listener.commandExecuted(more);
    }
  }

  @Override
  public void notifyInputRequested() {
    for (ConsoleCommunicationListener listener: communicationListeners) {
      listener.inputRequested();
    }
  }

  public VirtualFile getConsoleFile() {
    return myConsoleFile;
  }

  public void setConsoleFile(VirtualFile consoleFile) {
    myConsoleFile = consoleFile;
  }

  @Override
  public void notifyInputReceived() {
    if (waitingForInput) {
      waitingForInput = false;
      for (ConsoleCommunicationListener listener : communicationListeners) {
        listener.commandExecuted(false);
      }
    }
  }
}
