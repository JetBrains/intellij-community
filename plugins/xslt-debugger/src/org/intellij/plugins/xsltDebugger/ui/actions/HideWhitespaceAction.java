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
package org.intellij.plugins.xsltDebugger.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.ui.treeStructure.Tree;
import org.intellij.plugins.xsltDebugger.XsltDebuggerBundle;
import org.intellij.plugins.xsltDebugger.ui.GeneratedStructureModel;
import org.jetbrains.annotations.NotNull;

public class HideWhitespaceAction extends ToggleAction {
  private final Tree myStructureTree;
  private final GeneratedStructureModel myEventModel;

  public HideWhitespaceAction(Tree structureTree, GeneratedStructureModel eventModel) {
    super(XsltDebuggerBundle.message("action.hide.whitespace.nodes.text"));
    myStructureTree = structureTree;
    myEventModel = eventModel;

    getTemplatePresentation().setIcon(AllIcons.ObjectBrowser.FlattenPackages);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myEventModel.isFilterWhitespace();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final TreeState treeState = TreeState.createOn(myStructureTree);
    myEventModel.setFilterWhitespace(state);
    treeState.applyTo(myStructureTree);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
