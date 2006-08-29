/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class ObjectNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private ObjectTree myTree;

  private ObjectNode myParent;
  private Reference<Object> myReference;

  private List<ObjectNode> myChildren;
  private Throwable myTrace;

  public ObjectNode(ObjectTree tree, ObjectNode parentNode, Object object) {
    myTree = tree;
    myParent = parentNode;
    myReference = new WeakReference<Object>(object);

    if (Disposer.isDebugMode()) {
      myTrace = new Throwable();
    }
  }

  public void addChild(Object childObject) {
    ensureChildArray();

    final ObjectNode childNode = new ObjectNode(myTree, this, childObject);
    _add(childNode);
  }

  public void addChild(ObjectNode child) {
    ensureChildArray();
    _add(child);
  }

  public void removeChild(ObjectNode child) {
    _remove(child);
  }

  private void setParent(ObjectNode parent) {
    myParent = parent;
  }

  public ObjectNode getParent() {
    return myParent;
  }

  private void _add(final ObjectNode child) {
    child.setParent(this);
    myChildren.add(child);
    myTree.getObject2NodeMap().put(child.getObject(), child);
  }

  private void _remove(final ObjectNode child) {
    assert myChildren != null: "No chindren to remove child: " + this + ' ' + child;
    if (myChildren.remove(child)) {
      child.setParent(null);
      myTree.getObject2NodeMap().remove(child.getObject());
    }
  }

  private void ensureChildArray() {
    if (myChildren == null) {
      myChildren = new ArrayList<ObjectNode>();
    }
  }

  public void execute(boolean disposeTree, ObjectTreeAction action) {
    final Object object = myReference.get();
    if (object == null) return;

    if (myTree.getExecutedObjects().contains(this)) {
      return;
    }


    myTree.getExecutedObjects().add(this);

    if (myChildren != null) {
      final ObjectNode[] children = myChildren.toArray(new ObjectNode[myChildren.size()]);
      for (int i = children.length - 1; i >= 0; i--) {
        children[i].execute(disposeTree, action);
      }
    }

    if (disposeTree) {
      myChildren = null;
    }

    try {
      action.execute(object);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    if (disposeTree) {
      myTree.getObject2NodeMap().remove(object);
      if (myParent != null) {
        myParent.removeChild(this);
      }
      else {
        myTree.getRootObjects().remove(object);
      }
    }

    myTree.getExecutedObjects().remove(this);
  }

  @Nullable
  public Object getObject() {
    return myReference.get();
  }

  public List getChildren() {
    return myChildren;
  }

  @NonNls
  public String toString() {
    return "Node: " + myReference.toString();
  }


  public Throwable getTrace() {
    return myTrace;
  }
}
