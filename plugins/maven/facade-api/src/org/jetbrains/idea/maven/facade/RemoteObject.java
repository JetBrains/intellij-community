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

  public static Throwable wrapException(Throwable ex) {
    boolean foreignException = false;
    Throwable each = ex;
    while(each != null) {
      String name = each.getClass().getName();
      if (!name.startsWith("java") && !name.startsWith(RemoteObject.class.getPackage().getName())) {
        foreignException = true;
        break;
      }
      each = each.getCause();
    }

    if (foreignException) {
      Throwable wrapper = new Throwable(ex.toString());
      wrapper.setStackTrace(ex.getStackTrace());
      ex = wrapper;
    }
    return ex;
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
