package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 19, 2004
 * Time: 11:42:03 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeThread<E> {
  private static final int RESTART_TIMEOUT = 500;
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.InvokeThread");
  private final String myWorkerThreadName;

  private static int N = 0;

  protected static class WorkerThread<E> extends Thread {
    private final InvokeThread<E> myOwner;

    private boolean myIsInterrupted = false;

    protected WorkerThread(InvokeThread<E> owner, String name) {
      super(name + (N ++));
      myOwner = owner;
    }

    public void interrupt() {
      myIsInterrupted = true;
      super.interrupt();
    }

    public boolean isInterrupted() {
      return myIsInterrupted || super.isInterrupted();
    }

    public InvokeThread<E> getOwner() {
      return myOwner;
    }
  };

  protected final EventQueue<E> myEvents;

  private WorkerThread myWorkerThread = null;

  public InvokeThread(String name, int countPriorites) {
    myEvents = new EventQueue<E>(countPriorites);
    myWorkerThreadName = name;
    startNewWorkerThread();
  }

  protected abstract void processEvent(E e);

  protected void startNewWorkerThread() {
    WorkerThread workerThread = new WorkerThread(this, myWorkerThreadName) {
      public void run() {
        InvokeThread.this.run();
      }
    };
    myWorkerThread = workerThread;
    workerThread.start();
  }

  protected void restartWorkerThread() {
    getWorkerThread().interrupt();
    try {
      getWorkerThread().join(RESTART_TIMEOUT);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    startNewWorkerThread();
  }

  public void run() {
    Thread current = Thread.currentThread();

    for(;;) {
      try {
        if(current.isInterrupted()) break;

        if(getWorkerThread() != current) {
          LOG.assertTrue(false, "Expected " + current + " instead of " + getWorkerThread());
        }

        processEvent(myEvents.get());
      }
      catch (EventQueueClosedException e) {
        break;
      }
      catch (RuntimeException e) {
        if(e.getCause() instanceof InterruptedException) {
          break;
        } else {
          LOG.error(e);
        }
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Thread " + this.toString() + " exited");
    }
  }

  protected static InvokeThread currentThread() {
    Thread thread = Thread.currentThread();
    return thread instanceof WorkerThread ? ((WorkerThread)thread).getOwner() : null;
  }

  public void invokeLater(E r, int priority) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("invokeLater " + r + " in " + this);
    }
    myEvents.put(r, priority);
  }

  protected void switchToThread(WorkerThread newWorkerThread) {
    LOG.assertTrue(Thread.currentThread() instanceof WorkerThread);
    myWorkerThread = newWorkerThread;
    LOG.debug("Closing " + Thread.currentThread() + " new thread = " + newWorkerThread);
    Thread.currentThread().interrupt();
  }

  public Thread getWorkerThread() {
    return myWorkerThread;
  }

  public void close() {
    myEvents.close();
    LOG.debug("Closing evaluation");
  }
}
