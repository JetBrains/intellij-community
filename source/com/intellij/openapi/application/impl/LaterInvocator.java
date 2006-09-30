package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Stack;

public class LaterInvocator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.LaterInvocator");

  private static final Object LOCK = new Object();
  private static final IdeEventQueue ourEventQueue = IdeEventQueue.getInstance();

  private static class RunnableInfo {
    final Runnable runnable;
    final ModalityState modalityState;

    public RunnableInfo(Runnable runnable, ModalityState modalityState) {
      this.runnable = runnable;
      this.modalityState = modalityState;
    }
  }

  private static ArrayList ourModalEntities = new ArrayList();
  private static final ArrayList<RunnableInfo> ourQueue = new ArrayList<RunnableInfo>();
  private static int ourQueueSkipCount = 0; // optimization
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

    ArrayList<Window> result = new ArrayList<Window>();
    for (int i = 0; i < ourModalEntities.size(); i++) {
      Object entity = ourModalEntities.get(i);
      if (entity instanceof Window){
        result.add((Window)entity);
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
    LOG.assertTrue(removed);
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
    ApplicationManager.getApplication().assertIsDispatchThread();
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

  public static boolean isInMyRunnable() {
    // speed
    //LOG.assertTrue(EventQueue.isDispatchThread());

    if (ourEventStack.isEmpty()) return false;
    AWTEvent top = ourEventStack.peek();
    return ourEventQueue.getTrueCurrentEvent() == top;
  }

  private static void requestFlush() {
    SwingUtilities.invokeLater(ourFlushQueueRunnable);
  }

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

  private static Object RUN_LOCK = new Object();

  private static class FlushQueue implements Runnable {
    public void run() {
      Runnable runnable = pollNext();
      if (runnable != null) {
        synchronized (RUN_LOCK) { // necessary only because of switching to our own event queue
          AWTEvent event = ourEventQueue.getTrueCurrentEvent();
          ourEventStack.push(event);
          int stackSize = ourEventStack.size();

          try {
            runnable.run();
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
          }
        }

        requestFlush();
      }
    }
  }
}
