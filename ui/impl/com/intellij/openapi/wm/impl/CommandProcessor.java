package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;

import java.util.ArrayList;

public final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.CommandProcessor");
  private final Object myLock;
  private final ArrayList myCommandList;

  public CommandProcessor() {
    myLock = new Object();
    myCommandList = new ArrayList();
  }

  public final int getCommandCount() {
    synchronized (myLock) {
      return myCommandList.size();
    }
  }

  /**
   * Executes passed batch of commands. Note, that the processor surround the
   * commands with BlockFocusEventsCmd - UnbockFocusEventsCmd. It's required to
   * prevent focus handling of events which is caused by the commands to be executed.
   */
  public final void execute(final java.util.List commandList) {
    synchronized (myLock) {
      final boolean isBusy = myCommandList.size() > 0;
      myCommandList.addAll(commandList);
      if (!isBusy) {
        run();
      }
    }
  }

  public final void run() {
    synchronized (myLock) {
      if (myCommandList.size() > 0) {
        final FinalizableCommand command = (FinalizableCommand)myCommandList.remove(0);
        if (LOG.isDebugEnabled()) {
          LOG.debug("CommandProcessor.run " + command);
        }
        // max. I'm not actually quite sure this should have NON_MODAL modality but it should
        // definitely have some since runnables in command list may (and do) request some PSI activity
        ApplicationManager.getApplication().invokeLater(command, ModalityState.NON_MODAL);
      }
    }
  }
}
