package com.intellij.execution.junit2.segments;

import com.intellij.openapi.diagnostic.Logger;

import java.awt.*;

public class DeferedActionsQueueImpl implements DeferedActionsQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.segments.DeferedActionsQueueImpl");
  private DispatchListener myListener = DispatchListener.DEAF;
  private int myCounter = 0;

  public void addLast(final Runnable runnable) {
    checkIsDispatchThread();
    myListener.onStarted();
    try {
      runnable.run();
    } finally{
      myListener.onFinished();
    }
  }

  private void checkIsDispatchThread() {
    myCounter++;
    if (myCounter > 127) {
      myCounter = 0;
      LOG.assertTrue(EventQueue.isDispatchThread());
    }
  }

  public void setDispactchListener(final DispatchListener listener) {
    myListener = listener;
  }
}
