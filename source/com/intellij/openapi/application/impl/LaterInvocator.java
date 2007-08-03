package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

public class LaterInvocator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.LaterInvocator");
  private static final boolean DEBUG = LOG.isDebugEnabled();

  public static final Object LOCK = new Object(); //public for tests
  private static final IdeEventQueue ourEventQueue = IdeEventQueue.getInstance();

  private LaterInvocator() {
  }

  private static class RunnableInfo {
    final Runnable runnable;
    final ModalityState modalityState;

    public RunnableInfo(Runnable runnable, ModalityState modalityState) {
      this.runnable = runnable;
      this.modalityState = modalityState;
    }

    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + "] ";
    }
  }

  private static ArrayList<Object> ourModalEntities = new ArrayList<Object>();
  private static final ArrayList<RunnableInfo> ourQueue = new ArrayList<RunnableInfo>();
  private static volatile int ourQueueSkipCount = 0; // optimization
  private static final Runnable ourFlushQueueRunnable = new FlushQueue();

  private static Stack<AWTEvent> ourEventStack = new Stack<AWTEvent>();

  static boolean IS_TEST_MODE = false;

  private static EventDispatcher<ModalityStateListener> ourModalityStateMulticaster = EventDispatcher.create(ModalityStateListener.class);

  public static void addModalityStateListener(ModalityStateListener listener){
    ourModalityStateMulticaster.addListener(listener);
  }

  public static void removeModalityStateListener(ModalityStateListener listener){
    ourModalityStateMulticaster.removeListener(listener);
  }

  static ModalityStateEx modalityStateForWindow(Window window){
    int index = ourModalEntities.indexOf(window);
    if (index < 0){
      Window owner = window.getOwner();
      if (owner == null) return (ModalityStateEx)ApplicationManager.getApplication().getNoneModalityState();
      ModalityStateEx ownerState = modalityStateForWindow(owner);
      if (window instanceof Dialog && ((Dialog)window).isModal()) {
        return ownerState.appendEnitity(window);
      }
      else{
        return ownerState;
      }
    }

    ArrayList result = new ArrayList();
    for (int i = 0; i < ourModalEntities.size(); i++) {
      Object entity = ourModalEntities.get(i);
      if (entity instanceof Window){
        result.add(entity);
      } else if (entity instanceof ProgressIndicator) {
        if (((ProgressIndicator)entity).isModal()) {
          result.add(entity);
        }
      }
    }
    return new ModalityStateEx(result.toArray());
  }

  public static void invokeLater(Runnable runnable) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    invokeLater(runnable, modalityState);
  }

  public static void invokeLater(Runnable runnable, ModalityState modalityState) {
    LOG.assertTrue(modalityState != null);
    synchronized (LOCK) {
      ourQueue.add(new RunnableInfo(runnable, modalityState));
    }
    requestFlush();
  }

  public static void invokeAndWait(final Runnable runnable, ModalityState modalityState) {
    LOG.assertTrue(modalityState != null);
    LOG.assertTrue(!isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    Runnable runnable1 = new Runnable() {
      public void run() {
        try {
          runnable.run();
        }
        finally {
          semaphore.up();
        }
      }

      public String toString() {
        return "InvokeAndWait[" + runnable.toString() + "]";
      }
    };
    invokeLater(runnable1, modalityState);
    semaphore.waitFor();                                          
  }

  public static void enterModal(Object modalEnity) {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEnity);
    }

    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged();
    }

    ourModalEntities.add(modalEnity);
  }

  public static void leaveModal(Object modalEntity) {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread(), "leaveModal() should be invoked in event-dispatch thread");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged();
    }

    boolean removed = ourModalEntities.remove(modalEntity);
    LOG.assertTrue(removed, modalEntity);
    ourQueueSkipCount = 0;
    requestFlush();
  }

  static void leaveAllModals() {
    LOG.assertTrue(IS_TEST_MODE);

    /*
    if (!IS_TEST_MODE) {
      ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged();
    }
    */

    ourModalEntities.clear();
    ourQueueSkipCount = 0;
    requestFlush();
  }

  public static Object[] getCurrentModalEntities() {
    if (!IS_TEST_MODE) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
    //TODO!
    //LOG.assertTrue(IdeEventQueue.getInstance().isInInputEvent() || isInMyRunnable());

    return ourModalEntities.toArray(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public static boolean isInModalContext() {
    if (!IS_TEST_MODE) {
      LOG.assertTrue(isDispatchThread());
    }

    return !ourModalEntities.isEmpty();
  }

  private static boolean isDispatchThread() {
    return ApplicationManager.getApplication().isDispatchThread();
  }

  private static void requestFlush() {
    SwingUtilities.invokeLater(ourFlushQueueRunnable);
  }

  @Nullable
  private static Runnable pollNext() {
    synchronized (LOCK) {
      ModalityStateEx currentModality = (ModalityStateEx)(ourModalEntities.size() > 0
                                        ? new ModalityStateEx(ourModalEntities.toArray(ArrayUtil.EMPTY_OBJECT_ARRAY))
                                        : ApplicationManager.getApplication().getNoneModalityState());

      while(ourQueueSkipCount < ourQueue.size()){
        RunnableInfo info = ourQueue.get(ourQueueSkipCount);
        if (!currentModality.dominates(info.modalityState)) {
          ourQueue.remove(ourQueueSkipCount);
          return info.runnable;
        }
        ourQueueSkipCount++;
      }

      return null;
    }
  }

  private static final Object RUN_LOCK = new Object();

  static class FlushQueue implements Runnable {
    private Runnable myLastRunnable;

    public void run() {
      myLastRunnable = pollNext();

      if (myLastRunnable != null) {
        synchronized (RUN_LOCK) { // necessary only because of switching to our own event queue
          AWTEvent event = ourEventQueue.getTrueCurrentEvent();
          ourEventStack.push(event);
          int stackSize = ourEventStack.size();

          try {
            myLastRunnable.run();
          }
          catch (Throwable t) {
            if (t instanceof StackOverflowError){
              t.printStackTrace();
            }
            LOG.error(t);
          }
          finally {
            LOG.assertTrue(ourEventStack.size() == stackSize);
            ourEventStack.pop();

            if (!DEBUG) myLastRunnable = null;
          }
        }

        requestFlush();
      }
    }

    public String toString() {
      return "LaterInvocator[lastRunnable=" + myLastRunnable + "]";
    }
  }

  //tests only
  public static java.util.List<Object> dumpQueue() {
    synchronized (LOCK) {
      if (!ourQueue.isEmpty()) {
        ArrayList<Object> r = new ArrayList<Object>();
        r.addAll(ourQueue);
        Collections.reverse(r);
        return r;
      }
    }
    return null;
  }
}
