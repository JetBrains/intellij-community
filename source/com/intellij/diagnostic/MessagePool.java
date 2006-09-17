/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.apache.log4j.Category;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class MessagePool {

  private static int MAX_POOL_SIZE_FOR_FATALS = 2;

  private static MessagePool ourInstance;

  private List<AbstractMessage> myIdeFatals = new ArrayList<AbstractMessage>();

  private Set<MessagePoolListener> myListeners = new HashSet<MessagePoolListener>();

  private MessageGrouper myFatalsGrouper;
  private boolean ourJvmIsShuttingDown = false;

  MessagePool(int maxGroupSize, int timeout) {
    myFatalsGrouper = new MessageGrouper("Fatal Errors Grouper", timeout, maxGroupSize);
  }

  public static MessagePool getInstance() {
    if (ourInstance == null) {
      ourInstance = new MessagePool(20, 1000);
    }

    return ourInstance;
  }

  public void addIdeFatalMessage(LoggingEvent aEvent) {
    addIdeFatalMessage(new LogMessage(aEvent));
  }

  public void addIdeFatalMessage(IdeaLoggingEvent aEvent) {
    addIdeFatalMessage(new LogMessage(aEvent));
  }

  private void addIdeFatalMessage(LogMessage message) {
    if (myIdeFatals.size() < MAX_POOL_SIZE_FOR_FATALS) {
      myFatalsGrouper.add(message);
    } else if (myIdeFatals.size() == MAX_POOL_SIZE_FOR_FATALS) {
      myFatalsGrouper.add(new LogMessage(new LoggingEvent(DiagnosticBundle.message("error.monitor.too.many.errors"),
                                                          Category.getRoot(), Priority.ERROR, null, new TooManyErrorsException())));
    }
  }

  public boolean isFatalErrorsPoolEmpty() {
    return myIdeFatals.isEmpty();
  }

  public boolean hasUnreadMessages() {
    for (int i = 0; i < myIdeFatals.size(); i++) {
      AbstractMessage message = myIdeFatals.get(i);
      if (!message.isRead()) return true;
    }
    return false;
  }

  public List<AbstractMessage> getFatalErrors(boolean aIncludeReadMessages, boolean aIncludeSubmittedMessages) {
    List<AbstractMessage> result = new ArrayList<AbstractMessage>();
    for (int i = 0; i < myIdeFatals.size(); i++) {
      AbstractMessage each = myIdeFatals.get(i);
      if (!each.isRead() && !each.isSubmitted()) {
        result.add(each);
      } else if ((each.isRead() && aIncludeReadMessages) || (each.isSubmitted() && aIncludeSubmittedMessages)) {
        result.add(each);
      }
    }
    return result;
  }

  public void clearFatals() {
    myIdeFatals.clear();
    notifyListenersClear();
  }

  public void addListener(MessagePoolListener aListener) {
    myListeners.add(aListener);
  }

  public void removeListener(MessagePoolListener aListener) {
    myListeners.remove(aListener);
  }

  private void notifyListenersAdd() {
    if (ourJvmIsShuttingDown) return;

    final MessagePoolListener[] messagePoolListeners = myListeners.toArray(new MessagePoolListener[myListeners.size()]);
    for (int i = 0; i < messagePoolListeners.length; i++) {
      messagePoolListeners[i].newEntryAdded();
    }
  }

  private void notifyListenersClear() {
    final MessagePoolListener[] messagePoolListeners = myListeners.toArray(new MessagePoolListener[myListeners.size()]);
    for (int i = 0; i < messagePoolListeners.length; i++) {
      messagePoolListeners[i].poolCleared();
    }
  }

  public void setJvmIsShuttingDown() {
    ourJvmIsShuttingDown = true;
  }

  private class MessageGrouper extends Thread {

    private int myTimeOut;
    private int myMaxGroupSize;

    private final List<AbstractMessage> myMessages = new ArrayList<AbstractMessage>();
    private int myAccumulatedTime;

    public MessageGrouper(@NonNls String name, int timeout, int maxGroupSize) {
      setName(name);
      myTimeOut = timeout;
      myMaxGroupSize = maxGroupSize;
      start();
    }

    /** @noinspection BusyWait*/
    public void run() {
      while (true) {
        try {
          sleep(50);
          myAccumulatedTime += 50;
          if (myAccumulatedTime > myTimeOut) {
            synchronized(myMessages) {
              if (myMessages.size() > 0) {
                post();
              }
            }
          }
        } catch (InterruptedException e) {
          return;
        }
      }
    }

    private void post() {
      AbstractMessage message;
      if (myMessages.size() == 1) {
        message = myMessages.get(0);
      } else {
        message = new GroupedLogMessage(new ArrayList<AbstractMessage>(myMessages));
      }
      myMessages.clear();
      myIdeFatals.add(message);
      notifyListenersAdd();
      myAccumulatedTime = 0;
    }

    public void add(AbstractMessage message) {
      myAccumulatedTime = 0;
      synchronized(myMessages) {
        myMessages.add(message);
        if (myMessages.size() >= myMaxGroupSize) {
          post();
        }
      }
    }
  }

  static class TooManyErrorsException extends Exception {
    TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}
