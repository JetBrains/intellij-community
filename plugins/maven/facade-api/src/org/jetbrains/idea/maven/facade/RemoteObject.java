package org.jetbrains.idea.maven.facade;

import com.intellij.util.containers.ContainerUtil;

import java.lang.ref.WeakReference;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class RemoteObject implements Remote, Unreferenced {
  private final Map<RemoteObject, Remote> myChildren = new ConcurrentHashMap<RemoteObject, Remote>();
  private final WeakReference<RemoteObject> myWeakRef;
  private RemoteObject myParent;

  public RemoteObject() {
    myWeakRef = new WeakReference<RemoteObject>(this);
  }

  public WeakReference<RemoteObject> getWeakRef() {
    return myWeakRef;
  }

  protected <T extends Remote> T export(final T child) throws RemoteException {
    if (child == null) return null;
    final T result = (T)UnicastRemoteObject.exportObject(child, 0);
    myChildren.put((RemoteObject)child, result);
    ((RemoteObject)child).myParent = this;
    return result;
  }

  public void unexportChildren() throws RemoteException {
    final ArrayList<RemoteObject> childrenRefs = new ArrayList<RemoteObject>(myChildren.keySet());
    myChildren.clear();
    for (RemoteObject child : childrenRefs) {
      child.unreferenced();
    }
  }

  protected void unexportChildren(Collection<WeakReference<RemoteObject>> children) throws RemoteException {
    if (children.isEmpty()) return;
    final ArrayList<RemoteObject> list = new ArrayList<RemoteObject>(children.size());
    for (WeakReference<RemoteObject> child : children) {
      ContainerUtil.addIfNotNull(child.get(), list);
    }
    myChildren.keySet().removeAll(list);
    for (RemoteObject child : list) {
      child.unreferenced();
    }
  }

  protected static void handleException(Exception e) {
    Throwable cause = e;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    cause = wrapException(cause);
    throw cause instanceof RuntimeException ? (RuntimeException)cause : new RuntimeException(cause);
  }

  public static Throwable wrapException(Throwable ex) {
    if (!ex.getClass().getName().startsWith("java")) {
      final Throwable replaceWith = new RuntimeException(ex.toString());
      replaceWith.setStackTrace(ex.getStackTrace());
      ex = replaceWith;
    }
    return ex;
  }

  public Object wrapIfNeeded(Object o) throws RemoteException {
    if (o == null) return o;
    if (o.getClass().getClassLoader() == null ||
        o.getClass().getName().startsWith("com.intellij")) return o;
    return o.toString();
  }

  public void unreferenced() {
    if (myParent != null) {
      myParent.myChildren.remove(this);
      myParent = null;
      try {
        unexportChildren();
        UnicastRemoteObject.unexportObject(this, false);
      }
      catch (RemoteException e) {
      }
    }
  }
}
