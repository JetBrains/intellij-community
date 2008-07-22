/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class ObjectNode<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private ObjectTree<T> myTree;

  private ObjectNode<T> myParent;
  private T myObject;

  private LinkedHashSet<ObjectNode<T>> myChildren;
  private Throwable myTrace;

  public ObjectNode(ObjectTree<T> tree, ObjectNode<T> parentNode, T object) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    if (Disposer.isDebugMode()) {
      myTrace = new Throwable();
    }
  }

  public void addChild(T childObject) {
    ensureChildArray();

    final ObjectNode<T> childNode = new ObjectNode<T>(myTree, this, childObject);
    _add(childNode);
  }

  public void addChild(ObjectNode<T> child) {
    ensureChildArray();
    _add(child);
  }

  public void removeChild(ObjectNode<T> child) {
    _remove(child);
  }

  private void setParent(ObjectNode<T> parent) {
    myParent = parent;
  }

  public ObjectNode<T> getParent() {
    return myParent;
  }

  private void _add(final ObjectNode<T> child) {
    child.setParent(this);
    myChildren.add(child);
    myTree.getObject2NodeMap().put(child.getObject(), child);
  }

  private void _remove(final ObjectNode<T> child) {
    assert myChildren != null: "No chindren to remove child: " + this + ' ' + child;
    if (myChildren.remove(child)) {
      child.setParent(null);
      myTree.getObject2NodeMap().remove(child.getObject());
    }
  }

  private void ensureChildArray() {
    if (myChildren == null) {
      myChildren = new LinkedHashSet<ObjectNode<T>>();
    }
  }
  
  public boolean execute(boolean disposeTree, ObjectTreeAction<T> action) {
    if (myTree.getNodesInExecution().contains(this)) {
      return false;
    }

    myTree.getNodesInExecution().add(this);

    action.beforeTreeExecution(myObject);

    if (myChildren != null) {
//todo: [kirillk] optimize
      final ObjectNode<T>[] children = myChildren.toArray(new ObjectNode[myChildren.size()]);
      for (int i = children.length - 1; i >= 0; i--) {
        children[i].execute(disposeTree, action);
      }
    }

    if (disposeTree) {
      myChildren = null;
    }

    try {
      action.execute(myObject);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    if (disposeTree) {
      myTree.getObject2NodeMap().remove(myObject);
      if (myParent != null) {
        myParent.removeChild(this);
      }
      else {
        myTree.getRootObjects().remove(myObject);
      }
    }

    myTree.getNodesInExecution().remove(this);

    return true;
  }

  public T getObject() {
    return myObject;
  }

  public Collection<ObjectNode<T>> getChildren() {
    return myChildren;
  }

  @NonNls
  public String toString() {
    return "Node: " + myObject.toString();
  }


  public Throwable getTrace() {
    return myTrace;
  }
}
