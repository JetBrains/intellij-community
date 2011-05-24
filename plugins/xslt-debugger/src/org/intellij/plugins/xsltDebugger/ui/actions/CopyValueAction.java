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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.ide.CopyPasteManager;
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;
import org.intellij.plugins.xsltDebugger.ui.GeneratedStructureModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.datatransfer.StringSelection;

@SuppressWarnings({ "ComponentNotRegistered" })
public class CopyValueAction extends AnAction {
  public static final DataKey<DefaultMutableTreeNode> SELECTED_NODE = DataKey.create("SELECTED_NODE");

  public CopyValueAction(JComponent component) {
    final AnAction action = ActionManager.getInstance().getAction("$Copy");
    if (action != null) {
      copyFrom(action);
      registerCustomShortcutSet(getShortcutSet(), component);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  public void actionPerformed(AnActionEvent e) {
    final DefaultMutableTreeNode node = e.getData(SELECTED_NODE);
    if (node instanceof GeneratedStructureModel.StructureNode) {
      final GeneratedStructureModel.StructureNode structureNode = (GeneratedStructureModel.StructureNode)node;
      final OutputEventQueue.NodeEvent event = structureNode.getUserObject();
      setClipboardData(event.getValue());
    }
  }

  private static void setClipboardData(String value) {
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  protected static boolean isEnabled(AnActionEvent e) {
    final DefaultMutableTreeNode node = e.getData(SELECTED_NODE);
    if (node instanceof GeneratedStructureModel.StructureNode) {
      final GeneratedStructureModel.StructureNode structureNode = (GeneratedStructureModel.StructureNode)node;
      final OutputEventQueue.NodeEvent event = structureNode.getUserObject();
      return event != null && event.getValue() != null;
    }
    return false;
  }
}
