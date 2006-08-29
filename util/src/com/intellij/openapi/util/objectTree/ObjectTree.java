/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ObjectTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");
  private static Object FAKE_VALUE = new Object();

  private Map<Object, Object> myRootObjects = new WeakHashMap<Object, Object>();
  private Map<Object, ObjectNode> myObject2NodeMap = new com.intellij.util.containers.WeakHashMap<Object, ObjectNode>();

  private List<ObjectNode> myExecutedObjects = new ArrayList<ObjectNode>();

  public final Map<Object, ObjectNode> getObject2NodeMap() {
    return myObject2NodeMap;
  }

  public final List<ObjectNode> getExecutedObjects() {
    return myExecutedObjects;
  }

  public final Set<Object> getRootObjects() {
    Set<Object> result = new HashSet<Object>();

    for (Iterator<Object> i = myRootObjects.keySet().iterator(); i.hasNext();) {
      Object object = i.next();
      if (object != null) {
        result.add(object);
      }
      else {
        i.remove();
      }
    }

    return result;
  }

  public final void register(Object parent, Object child) {
    checkIfValid(child);

    final ObjectNode parentNode = getNodeFor(parent);
    final ObjectNode childNode = getNodeFor(child);

    if (childNode.getParent() != parentNode && childNode.getParent() != null) {
      childNode.getParent().removeChild(childNode);
      parentNode.addChild(childNode);
    }
    else if (myRootObjects.containsKey(child)) {
      final ObjectNode parentless = getNodeFor(child);
      parentNode.addChild(parentless);
      myRootObjects.remove(child);
    }
    else {
      parentNode.addChild(child);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Registered: " + child + " for parent " + parent);
    }
  }

  private void checkIfValid(Object child) {
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

  @NotNull
  private ObjectNode getNodeFor(Object parentObject) {
    final ObjectNode parentNode = getObject2NodeMap().get(parentObject);

    if (parentNode != null) return parentNode;

    final ObjectNode parentless = new ObjectNode(this, null, parentObject);
    myRootObjects.put(parentObject, FAKE_VALUE);
    getObject2NodeMap().put(parentObject, parentless);
    return parentless;
  }

  public final void executeAll(Object object, boolean disposeTree, ObjectTreeAction action) {
    assert object != null : "Unable execute action for null object";

    final ObjectNode node = getObject2NodeMap().get(object);

    if (node != null) {
      node.execute(disposeTree, action);
    }
    else {
      action.execute(object);
    }
  }

  public final void executeChildAndReplace(Object toExecute, Object toReplace, boolean disposeTree, ObjectTreeAction action) {
    final ObjectNode toExecuteNode = getObject2NodeMap().get(toExecute);
    assert toExecuteNode != null : "Object " + toExecute + " wasn't registered or already disposed";

    final ObjectNode parent = toExecuteNode.getParent();
    assert parent != null : "Object " + toExecute + " is not connected to the tree - doesn't have parent";

    toExecuteNode.execute(disposeTree, action);
    register(parent.getObject(), toReplace);
  }

}
