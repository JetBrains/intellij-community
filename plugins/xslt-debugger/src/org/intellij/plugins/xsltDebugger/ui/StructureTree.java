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

import com.intellij.openapi.actionSystem.*;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import org.intellij.plugins.xsltDebugger.ui.actions.CopyValueAction;
import org.intellij.plugins.xsltDebugger.ui.actions.NavigateAction;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 17.06.2007
 */
public class StructureTree extends Tree implements TypeSafeDataProvider {
  public StructureTree(GeneratedStructureModel model) {
    super(model);

    setCellRenderer(new GeneratedStructureRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);

    final DefaultActionGroup structureContextActions = new DefaultActionGroup("StructureContext", true);
    structureContextActions.add(NavigateAction.getInstance());
    structureContextActions.add(new CopyValueAction(this));
    PopupHandler.installFollowingSelectionTreePopup(this, structureContextActions, "XSLT.Debugger.GeneratedStructure", ActionManager.getInstance());
  }

  public void calcData(DataKey key, DataSink sink) {
    if (key.equals(CommonDataKeys.NAVIGATABLE)) {
      final TreePath selection = getSelectionPath();
      if (selection != null) {
        final Object o = selection.getLastPathComponent();
        if (o instanceof Navigatable) {
          sink.put(CommonDataKeys.NAVIGATABLE, (Navigatable)o);
        }
      }
    } else if (key.equals(CopyValueAction.SELECTED_NODE)) {
      final TreePath selection = getSelectionPath();
      if (selection != null) {
        final Object o = selection.getLastPathComponent();
        sink.put(CopyValueAction.SELECTED_NODE, (DefaultMutableTreeNode)o);
      }
    }
  }
}
