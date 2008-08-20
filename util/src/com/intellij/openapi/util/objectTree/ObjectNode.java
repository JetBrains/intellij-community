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
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.util.LinkedHashSet;

public final class ObjectNode<T> {
  private static final ObjectNode[] EMPTY_ARRAY = new ObjectNode[0];

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectNode");

  private final ObjectTree<T> myTree;

  private ObjectNode<T> myParent;
  private final T myObject;

  private LinkedHashSet<ObjectNode<T>> myChildren;
  private final Throwable myTrace;

  public ObjectNode(ObjectTree<T> tree, ObjectNode<T> parentNode, T object) {
    myTree = tree;
    myParent = parentNode;
    myObject = object;

    myTrace = Disposer.isDebugMode() ? new Throwable() : null;
  }

  private ObjectNode<T>[] getChildrenArray() {
    synchronized (myTree.treeLock) {
      if (myChildren == null) return EMPTY_ARRAY;
      return myChildren.toArray(new ObjectNode[myChildren.size()]);
    }
  }
  public void addChild(T childObject) {
    addChild(new ObjectNode<T>(myTree, this, childObject));
  }

  public void addChild(ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      ensureChildArray();
      child.setParent(this);
      myChildren.add(child);
      myTree.putNode(child.getObject(), child);
    }
  }

  public void removeChild(ObjectNode<T> child) {
    synchronized (myTree.treeLock) {
      assert myChildren != null: "No children to remove child: " + this + ' ' + child;
      if (myChildren.remove(child)) {
        child.setParent(null);
        myTree.putNode(child.getObject(), null);
      }
    }
  }

  private void setParent(ObjectNode<T> parent) {
    synchronized (myTree.treeLock) {
      myParent = parent;
    }
  }

  public ObjectNode<T> getParent() {
    return myParent;
  }

  private void ensureChildArray() {
    if (myChildren == null) {
      myChildren = new LinkedHashSet<ObjectNode<T>>();
    }
  }
  
  boolean execute(final boolean disposeTree, final ObjectTreeAction<T> action) {
    ObjectTree.executeActionWithRecursiveGuard(this, new ObjectTreeAction<ObjectNode<T>>() {
      public void execute(ObjectNode<T> each) {
        action.beforeTreeExecution(myObject);

        ObjectNode<T>[] childrenArray = getChildrenArray();
//todo: [kirillk] optimize

          for (int i = childrenArray.length - 1; i >= 0; i--) {
            childrenArray[i].execute(disposeTree, action);
          }

        if (disposeTree) {
          synchronized (myTree.treeLock) {
            myChildren = null;
          }
        }

        try {
          action.execute(myObject);
        }
        catch (Throwable e) {
          LOG.error(e);
        }

        if (disposeTree) {
          myTree.putNode(myObject, null);
          synchronized (myTree.treeLock) {
            if (myParent != null) {
              myParent.removeChild(ObjectNode.this);
            }
            else {
              myTree.removeRootObject(myObject);
            }
          }
        }
      }

      public void beforeTreeExecution(ObjectNode<T> parent) {

      }
    }, myTree.getNodesInExecution());

    return true;
  }

  public T getObject() {
    return myObject;
  }

  @NonNls
  public String toString() {
    return "Node: " + myObject.toString();
  }

  Throwable getTrace() {
    return myTrace;
  }

  @TestOnly
  void assertNoReferencesKept(T aDisposable) {
    Assert.assertNotSame(getObject(), aDisposable);
    if (myChildren != null) {
      for (ObjectNode<T> node: myChildren) {
        node.assertNoReferencesKept(aDisposable);
      }
    }
  }
}
