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
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ObjectTree<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");

  private Set<T> myRootObjects = new THashSet<T>(TObjectHashingStrategy.IDENTITY);
  private Map<T, ObjectNode<T>> myObject2NodeMap = new IdentityHashMap<T, ObjectNode<T>>();

  private List<ObjectNode<T>> myExecutedNodes = new ArrayList<ObjectNode<T>>();
  private List<T> myExecutedUnregisteredNodes = new ArrayList<T>();

  public final Map<T, ObjectNode<T>> getObject2NodeMap() {
    return myObject2NodeMap;
  }

  public final List<ObjectNode<T>> getNodesInExecution() {
    return myExecutedNodes;
  }

  public final Set<T> getRootObjects() {
    return myRootObjects;
  }

  public final void register(T parent, T child) {
    checkIfValid(child);

    final ObjectNode<T> parentNode = getNodeFor(parent);
    final ObjectNode<T> childNode = getNodeFor(child);

    ObjectNode<T> childParent = childNode.getParent();
    if (childParent != parentNode && childParent != null) {
      childParent.removeChild(childNode);
      parentNode.addChild(childNode);
    }
    else if (myRootObjects.contains(child)) {
      parentNode.addChild(childNode);
      myRootObjects.remove(child);
    }
    else {
      parentNode.addChild(child);
    }
  }

  private void checkIfValid(T child) {
    ObjectNode childNode = myObject2NodeMap.get(child);
    boolean childIsInTree = childNode != null && childNode.getParent() != null;
    if (!childIsInTree) return;

    ObjectNode eachParent = childNode.getParent();
    while (eachParent != null) {
      if (eachParent.getObject() == child) {
        LOG.assertTrue(false, child + " was already added as a child of: " + eachParent);
      }
      eachParent = eachParent.getParent();
    }
  }

  private ObjectNode<T> getNodeFor(T parentObject) {
    final ObjectNode<T> parentNode = getObject2NodeMap().get(parentObject);

    if (parentNode != null) return parentNode;

    final ObjectNode<T> parentless = new ObjectNode<T>(this, null, parentObject);
    myRootObjects.add(parentObject);
    getObject2NodeMap().put(parentObject, parentless);
    return parentless;
  }

  public final boolean executeAll(T object, boolean disposeTree, ObjectTreeAction<T> action, boolean processUnregistered) {
    assert object != null : "Unable execute action for null object";

    ObjectNode<T> node = getObject2NodeMap().get(object);
    if (node == null) {
      if (processUnregistered) {
        executeUnregistered(object, action);
        return true;
      } else {
        return false;
      }
    }
    else {
      return node.execute(disposeTree, action);
    }
  }

  static <T> void executeActionWithRecursiveGuard(T object, final ObjectTreeAction<T> action, List<T> recursiveGuard) {
    if (ArrayUtil.indexOf(recursiveGuard, object, Equality.IDENTITY) != -1) return;

    recursiveGuard.add(object);
    try {
      action.execute(object);
    }
    finally {
      recursiveGuard.remove(object);
    }
  }

  private void executeUnregistered(final T object, final ObjectTreeAction<T> action) {
    executeActionWithRecursiveGuard(object, action, myExecutedUnregisteredNodes);
  }

  public final void executeChildAndReplace(T toExecute, T toReplace, boolean disposeTree, ObjectTreeAction<T> action) {
    final ObjectNode<T> toExecuteNode = getObject2NodeMap().get(toExecute);
    assert toExecuteNode != null : "Object " + toExecute + " wasn't registered or already disposed";

    final ObjectNode<T> parent = toExecuteNode.getParent();
    assert parent != null : "Object " + toExecute + " is not connected to the tree - doesn't have parent";

    toExecuteNode.execute(disposeTree, action);
    register(parent.getObject(), toReplace);
  }

  public final boolean isRegistered(T object) {
    return getObject2NodeMap().containsKey(object);
  }

  @Nullable
  public final T getParent(T object) {
    ObjectNode<T> parent = getObject2NodeMap().get(object).getParent();
    if (parent == null) return null;
    return parent.getObject();
  }

  public boolean isRoot(T object) {
    return myRootObjects.contains(object);
  }
}
