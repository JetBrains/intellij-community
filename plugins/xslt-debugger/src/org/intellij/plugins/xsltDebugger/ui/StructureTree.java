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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import org.intellij.plugins.xsltDebugger.ui.actions.CopyValueAction;
import org.intellij.plugins.xsltDebugger.ui.actions.NavigateAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class StructureTree extends Tree implements UiDataProvider {
  public StructureTree(GeneratedStructureModel model) {
    super(model);

    setCellRenderer(new GeneratedStructureRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);

    final DefaultActionGroup structureContextActions = DefaultActionGroup.createPopupGroup(() -> "StructureContext");
    structureContextActions.add(NavigateAction.getInstance());
    structureContextActions.add(new CopyValueAction(this));
    PopupHandler.installFollowingSelectionTreePopup(this, structureContextActions, "XSLT.Debugger.GeneratedStructure");
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    TreePath selection = getSelectionPath();
    Object obj = selection == null ? null : selection.getLastPathComponent();
    sink.set(CommonDataKeys.NAVIGATABLE, obj instanceof Navigatable o ? o : null);
    sink.set(CopyValueAction.SELECTED_NODE, obj instanceof DefaultMutableTreeNode o? o : null);
  }
}
