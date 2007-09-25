package com.intellij.openapi.components.impl;

import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

/**
 * @author mike
 */
public class CachingComponentAdapter extends DecoratingComponentAdapter implements LifecycleManager {

  private ObjectReference instanceReference;
  private boolean disposed;
  private boolean started;
  private boolean delegateHasLifecylce;
  private JBReentrantReadWriteLock rw = LockFactory.createReadWriteLock();
  private JBLock r = rw.readLock();
  private JBLock w = rw.writeLock();

  public CachingComponentAdapter(ComponentAdapter delegate) {
    this(delegate, new SimpleReference());
  }

  public CachingComponentAdapter(ComponentAdapter delegate, ObjectReference instanceReference) {
    super(delegate);
    this.instanceReference = instanceReference;
    disposed = false;
    started = false;
    delegateHasLifecylce =
      delegate instanceof LifecycleStrategy && ((LifecycleStrategy)delegate).hasLifecycle(delegate.getComponentImplementation());
  }


  public Object getComponentInstance(PicoContainer container)
    throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {

    r.lock();
    Object instance;
    try {
      instance = instanceReference.get();
      if (instance != null) return instance;
    }
    finally {
      r.unlock();
    }

    w.lock();
    try {
      instance = instanceReference.get();
      if (instance == null) {
        instance = super.getComponentInstance(container);
        instanceReference.set(instance);
      }
      return instance;
    }
    finally {
      w.unlock();
    }
  }

  /**
   * Starts the cached component instance
   * {@inheritDoc}
   */
  public void start(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      if (started) throw new IllegalStateException("Already started");
      start(getComponentInstance(container));
      started = true;
    }
  }

  /**
   * Stops the cached component instance
   * {@inheritDoc}
   */
  public void stop(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      if (!started) throw new IllegalStateException("Not started");
      stop(getComponentInstance(container));
      started = false;
    }
  }

  /**
   * Disposes the cached component instance
   * {@inheritDoc}
   */
  public void dispose(PicoContainer container) {
    if (delegateHasLifecylce) {
      if (disposed) throw new IllegalStateException("Already disposed");
      dispose(getComponentInstance(container));
      disposed = true;
    }
  }

  public boolean hasLifecycle() {
    return delegateHasLifecylce;
  }

}
