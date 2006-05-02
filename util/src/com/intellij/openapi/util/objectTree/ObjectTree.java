/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util.objectTree;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ObjectTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.objectTree.ObjectTree");

  private Set<Object> myRootObjects = new THashSet<Object>();
  private Map<Object, ObjectNode> myObject2NodeMap = new THashMap<Object, ObjectNode>();

  private List<ObjectNode> myExecutedObjects = new ArrayList<ObjectNode>();

  public final Map<Object, ObjectNode> getObject2NodeMap() {
    return myObject2NodeMap;
  }

  public final List<ObjectNode> getExecutedObjects() {
    return myExecutedObjects;
  }

  public final Set getRootObjects() {
    return myRootObjects;
  }

  public final void register(Object parent, Object child) {
    checkIfValid(child);

    final ObjectNode parentNode = getNodeFor(parent);
    final ObjectNode childNode = getNodeFor(child);

    if (childNode != null && childNode.getParent() != parentNode && childNode.getParent() != null) {
      childNode.getParent().removeChild(childNode);
      parentNode.addChild(childNode);
    }
    else if (myRootObjects.contains(child)) {
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

  private ObjectNode getNodeFor(Object parentObject) {
    final ObjectNode parentNode = getObject2NodeMap().get(parentObject);

    if (parentNode != null) return parentNode;

    final ObjectNode parentless = new ObjectNode(this, null, parentObject);
    myRootObjects.add(parentObject);
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

  public final boolean isRegistered(Object object) {
    return getObject2NodeMap().containsKey(object);
  }

  public final Object getParent(Object object) {
    ObjectNode parent = getObject2NodeMap().get(object).getParent();
    if (parent == null) return null;
    return parent.getObject();
  }

  public boolean isRoot(Object object) {
    return myRootObjects.contains(object);
  }
}
