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

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.ui.treeStructure.Tree;
import icons.XsltDebuggerIcons;
import org.intellij.plugins.xsltDebugger.ui.GeneratedStructureModel;

@SuppressWarnings({ "ComponentNotRegistered" })
public class HideWhitespaceAction extends ToggleAction {
  private final Tree myStructureTree;
  private final GeneratedStructureModel myEventModel;

  public HideWhitespaceAction(Tree structureTree, GeneratedStructureModel eventModel) {
    super("Hide Whitespace Nodes");
    myStructureTree = structureTree;
    myEventModel = eventModel;

    getTemplatePresentation().setIcon(XsltDebuggerIcons.Actions.FilterWhitespace);
  }

  public boolean isSelected(AnActionEvent e) {
    return myEventModel.isFilterWhitespace();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    final TreeState treeState = TreeState.createOn(myStructureTree);
    myEventModel.setFilterWhitespace(state);
    treeState.applyTo(myStructureTree);
  }
}
