// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.actions;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.NlsSafe;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.console.PydevConsoleExecuteActionHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Service for command queue in Python console.
 * It has own listener(CommandQueueListener myListener), which it notifies about the changes in the command queue.
 */
@Service
public final class CommandQueueForPythonConsoleService {
  private final Map<ConsoleCommunication, CommandQueueListener> myListeners = new ConcurrentHashMap<>();
  private final Map<ConsoleCommunication, Queue<ConsoleCommunication.ConsoleCodeFragment>> queues = new ConcurrentHashMap<>();
  private final Map<ConsoleCommunication, PydevConsoleExecuteActionHandler> handlers = new ConcurrentHashMap<>();

  @NlsSafe
  private static final String STUB = "pass";

  @Nullable
  private Queue<ConsoleCommunication.ConsoleCodeFragment> getQueue(@NotNull ConsoleCommunication consoleComm) {
    return queues.get(consoleComm);
  }

  /**
   * Add new listener that is responsible for drawing the command queue.
   *
   * @param consoleComm a console communication corresponding to a console instance for listening events
   * @param listener    instance of a listener which will receive notifications from the service, responsible for the correct rendering of the panel
   */
  public synchronized void addListener(@NotNull ConsoleCommunication consoleComm, @NotNull CommandQueueListener listener) {
    myListeners.put(consoleComm, listener);
  }

  public synchronized void removeListener(@NotNull ConsoleCommunication consoleComm) {
    myListeners.remove(consoleComm);
  }

  @Nullable
  public synchronized ConsoleCommunication.ConsoleCodeFragment getFirstCommand(@NotNull ConsoleCommunication consoleComm) {
    var queue = getQueue(consoleComm);
    if (queue == null) return null;
    for (var elem : queue) {
      if (!elem.getText().equals(STUB)) {
        return elem;
      }
    }
    return null;
  }

  public boolean isEmpty(@NotNull ConsoleCommunication consoleComm) {
    if (consoleComm instanceof PydevConsoleCommunication) {
      if (((PydevConsoleCommunication)consoleComm).isCommunicationClosed()) {
        return true;
      }
    }
    var queue = getQueue(consoleComm);
    if (queue == null) return true;
    return queue.isEmpty();
  }

  public synchronized boolean isOneElement(@NotNull ConsoleCommunication consoleComm) {
    var queue = getQueue(consoleComm);
    if (queue == null) return false;
    return queue.size() == 1;
  }

  public synchronized boolean isTwoElement(@NotNull ConsoleCommunication consoleComm) {
    var queue = getQueue(consoleComm);
    if (queue == null) return false;
    return queue.size() == 2;
  }

  /**
   * Remove commands from queue and notify CommandQueueListener.
   *
   * @param consoleComm       a console communication corresponding to a console instance
   * @param exceptionOccurred if true then an exception occurred while executing the command, we should clear the queue else remove one command
   */
  public synchronized void removeCommand(@NotNull ConsoleCommunication consoleComm, boolean exceptionOccurred) {
    var queue = getQueue(consoleComm);
    if (queue == null) return;
    if (!queue.isEmpty()) {
      if (exceptionOccurred) {
        int value = queue.size();
        if (value > 1) {
          handlers.get(consoleComm).decreaseInputPromptCount(value - 1);
        }

        queue.clear();
        CommandQueueListener listener = myListeners.get(consoleComm);
        if (listener != null) {
          listener.removeAll();
        }
      }
      else {
        var command = queue.remove();
        if (!command.getText().isBlank() && !command.getText().equals(STUB)) {
          myListeners.get(consoleComm).removeCommand(command);
        }

        if (!queue.isEmpty()) {
          execCommand(consoleComm, queue.peek());
        }
      }
    }
  }

  /**
   * Calls if user remove command from queue using the button.
   * When using IPython, in order not to break the numbering of cells, inserts `pass` into the queue instead of a `codeFragment`.
   *
   * @param consoleComm  a console communication corresponding to a console instance
   * @param codeFragment a text of the command that a user deleted
   */
  public synchronized void removeCommand(@NotNull ConsoleCommunication consoleComm,
                                         @NotNull ConsoleCommunication.ConsoleCodeFragment codeFragment) {
    var queue = getQueue(consoleComm);
    if (queue == null) return;
    if (!queue.isEmpty()) {
      for (var code : queue) {
        if (code.equals(codeFragment)) {
          code.setText(STUB);
          break;
        }
      }
    }
  }

  public synchronized void addNewCommand(@NotNull PydevConsoleExecuteActionHandler pydevConsoleExecuteActionHandler,
                                         @NotNull ConsoleCommunication.ConsoleCodeFragment code) {
    var console = pydevConsoleExecuteActionHandler.getConsoleCommunication();
    if (console.isWaitingForInput()) {
      execCommand(console, code);
      return;
    }
    if (!queues.containsKey(console)) {
      queues.put(console, new ConcurrentLinkedDeque<>());
      handlers.put(console, pydevConsoleExecuteActionHandler);
    }
    var queue = getQueue(console);
    if (queue == null) return;
    if (!code.getText().isBlank()) {
      queue.add(code);
      myListeners.get(console).addCommand(code);
    }

    if (queue.size() == 1) {
      execCommand(console, code);
    }

    pydevConsoleExecuteActionHandler.updateConsoleState();
  }

  /**
   * Disable Command Queue for all Python Consoles.
   * Notify listeners that queues are disabled.
   */
  public synchronized void disableCommandQueue() {
    for (var queue : queues.values()) {
      if (queue != null) {
        queue.clear();
      }
    }

    for (var listener : myListeners.values()) {
      if (listener != null) {
        listener.disableConsole();
      }
    }
  }

  private static void execCommand(@NotNull ConsoleCommunication comm, @NotNull ConsoleCommunication.ConsoleCodeFragment code) {
    comm.execInterpreter(code, x -> null);
  }
}