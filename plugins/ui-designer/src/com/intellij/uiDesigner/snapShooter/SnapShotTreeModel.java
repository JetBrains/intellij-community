// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  private final SnapShotClient myClient;
  private final SnapShotRemoteComponent myRoot;

  public SnapShotTreeModel(final SnapShotClient client) {
    myClient = client;
    myRoot = new SnapShotRemoteComponent(0, Object.class.getName(), "", "Root");
  }

  @Override
  public Object getRoot() {
    return myRoot;
  }

  @Override
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

  @Override
  public int getChildCount(Object parent) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) parent;
    return checkGetChildren(component).length;
  }

  @Override
  public boolean isLeaf(Object node) {
    SnapShotRemoteComponent component = (SnapShotRemoteComponent) node;
    return checkGetChildren(component).length == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
  }

  @Override
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

  @Override
  public void addTreeModelListener(TreeModelListener l) {
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
  }
}
