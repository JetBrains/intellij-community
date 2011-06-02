/*
 * Copyright 2002-2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 09.06.2007
 */
public class SmartStructureTracker extends TreeModelAdapter {
  private final JTree myEventTree;
  private final Alarm myAlarm;

  public SmartStructureTracker(JTree eventTree, Disposable disposable) {
    myEventTree = eventTree;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable);
  }

  @Override
  public void treeNodesInserted(TreeModelEvent e) {
    final TreePath path = e.getTreePath();
    final Object child = e.getChildren()[0];
    if (path != null && child != null) {
      myAlarm.cancelAllRequests();
      final Runnable runnable = new Runnable() {
        public void run() {
          myEventTree.expandPath(path);
          TreeUtil.showRowCentered(myEventTree, myEventTree.getRowForPath(TreeUtil.getPathFromRoot((TreeNode)child)), false);
        }
      };
      myAlarm.addRequest(runnable, 300);
    }
  }

  @Override
  public void treeNodesRemoved(TreeModelEvent e) {
    final TreePath p = e.getTreePath();
    if (p != null) {
      if (p.getPathCount() > 1) {
        final Runnable runnable = new Runnable() {
          public void run() {
            DefaultMutableTreeNode last = (DefaultMutableTreeNode)p.getLastPathComponent();
            if (last.getChildCount() > 0) {
              DefaultMutableTreeNode next = (DefaultMutableTreeNode)last.getFirstChild();
              while (next != null) {
                boolean collapse = true;
//                                final int count = next.getChildCount();
//                                if (count > 0) {
//                                    for (int i = 0; i < count; i++) {
//                                        final DefaultMutableTreeNode child = (DefaultMutableTreeNode)next.getChildAt(i);
//                                        if (child instanceof GeneratedStructureModel.StructureNode) {
//                                            if (((GeneratedStructureModel.StructureNode)child).isNew()) {
//                                                collapse = false;
//                                            }
//                                        }
//                                    }
//                                }
                if (collapse) {
                  myEventTree.collapsePath(TreeUtil.getPathFromRoot(next));
                }
                next = next.getNextSibling();
              }
            }
          }
        };
        ApplicationManager.getApplication().invokeLater(runnable);
      }
    }
  }
}
