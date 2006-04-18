/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.openapi.ui.Messages;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;

/**
 * @author yole
 */
public class SnapShotTreeModel implements TreeModel {
  private SnapShotClient myClient;
  private SnapShotRemoteComponent myRoot;

  public SnapShotTreeModel(final SnapShotClient client) {
    myClient = client;
    myRoot = new SnapShotRemoteComponent(0, Object.class.getName(), "", "Root");
  }

  public Object getRoot() {
    return myRoot;
  }

  public Object getChild(Object parent, int index) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) parent;
    return checkGetChildren(component) [index];
  }

  private SnapShotRemoteComponent[] checkGetChildren(final SnapShotRemoteComponent component) {
    if (component.getChildren() == null) {
      try {
        component.setChildren(myClient.listChildren(component.getId()));
      }
      catch (IOException e) {
        reportDisconnection(myClient);
        return new SnapShotRemoteComponent[0];
      }
    }
    return component.getChildren();
  }

  private static void reportDisconnection(final SnapShotClient client) {
    Messages.showMessageDialog("Disconnected from remote application", "Create Form Snapshot",
                               Messages.getErrorIcon());
    client.setDisconnected();
  }

  public int getChildCount(Object parent) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) parent;
    return checkGetChildren(component).length;
  }

  public boolean isLeaf(Object node) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) node;
    return checkGetChildren(component).length == 0;
  }

  public void valueForPathChanged(TreePath path, Object newValue) {
  }

  public int getIndexOfChild(Object parent, Object child) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) parent;
    final SnapShotRemoteComponent[] snapShotRemoteComponents = checkGetChildren(component);
    for(int i=0; i<snapShotRemoteComponents.length; i++) {
      if (snapShotRemoteComponents [i] == child) {
        return i;
      }
    }
    return -1;
  }

  public void addTreeModelListener(TreeModelListener l) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void removeTreeModelListener(TreeModelListener l) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
