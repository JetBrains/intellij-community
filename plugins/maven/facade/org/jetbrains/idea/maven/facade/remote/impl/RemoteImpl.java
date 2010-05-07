package org.jetbrains.idea.maven.facade.remote.impl;

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

/**
 * @author Gregory.Shrago
 */
public class RemoteImpl implements Remote, Unreferenced {
  private final Map<RemoteImpl, Remote> myChildren = new ConcurrentHashMap<RemoteImpl, Remote>();
  private final WeakReference<RemoteImpl> myWeakRef;
  private RemoteImpl myParent;

  public RemoteImpl() {
    myWeakRef = new WeakReference<RemoteImpl>(this);
  }

  public WeakReference<RemoteImpl> getWeakRef() {
    return myWeakRef;
  }

  protected <T extends Remote> T export(final T child) throws RemoteException {
    if (child == null) return null;
    final T result = (T)UnicastRemoteObject.exportObject(child, 0);
    myChildren.put((RemoteImpl)child, result);
    ((RemoteImpl)child).myParent = this;
    return result;
  }

  protected <T extends Remote> T export2(final T child) throws RemoteException {
    return export(child);
  }

  public void unexportChildren() throws RemoteException {
    final ArrayList<RemoteImpl> childrenRefs = new ArrayList<RemoteImpl>(myChildren.keySet());
    myChildren.clear();
    for (RemoteImpl child : childrenRefs) {
      child.unreferenced();
    }
  }

  protected void unexportChildren(Collection<WeakReference<RemoteImpl>> children) throws RemoteException {
    if (children.isEmpty()) return;
    final ArrayList<RemoteImpl> list = new ArrayList<RemoteImpl>(children.size());
    for (WeakReference<RemoteImpl> child : children) {
      ContainerUtil.addIfNotNull(child.get(), list);
    }
    myChildren.keySet().removeAll(list);
    for (RemoteImpl child : list) {
      child.unreferenced();
    }
  }

  protected static void handleException(Exception e) {
    Throwable cause = e;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    if (!cause.getClass().getName().startsWith("java")) {
      final Throwable replaceWith = new RuntimeException(cause.toString());
      replaceWith.setStackTrace(cause.getStackTrace());
      cause = replaceWith;
    }
    throw cause instanceof RuntimeException ? (RuntimeException)cause : new RuntimeException(cause);
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
