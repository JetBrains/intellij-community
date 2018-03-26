/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.xml.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ElementPresentation;
import com.intellij.util.xml.tree.BaseDomElementNode;
import com.intellij.util.xml.tree.DomFileElementNode;
import com.intellij.util.xml.tree.DomModelTreeView;
import org.jetbrains.annotations.NotNull;

public class DeleteDomElement extends BaseDomTreeAction {

  public DeleteDomElement() {
  }

  public DeleteDomElement(final DomModelTreeView treeView) {
    super(treeView);
  }

  @Override
  public void actionPerformed(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();

    if (selectedNode instanceof BaseDomElementNode) {

      if (selectedNode instanceof DomFileElementNode) {
        e.getPresentation().setVisible(false);
        return;
      }
      
      final DomElement domElement = ((BaseDomElementNode)selectedNode).getDomElement();

      final int ret = Messages.showOkCancelDialog(getPresentationText(selectedNode, "Remove") + "?", "Remove",
                                                  Messages.getQuestionIcon());
      if (ret == Messages.OK) {
        WriteCommandAction.writeCommandAction(domElement.getManager().getProject(), DomUtil.getFile(domElement)).run(() -> {
          domElement.undefine();
        });
      }
    }
  }

  @Override
  public void update(AnActionEvent e, DomModelTreeView treeView) {
    final SimpleNode selectedNode = treeView.getTree().getSelectedNode();

    if (selectedNode instanceof DomFileElementNode) {
      e.getPresentation().setVisible(false);
      return;
    }

    boolean enabled = false;
    if (selectedNode instanceof BaseDomElementNode) {
      final DomElement domElement = ((BaseDomElementNode)selectedNode).getDomElement();
      if (domElement.isValid() && DomUtil.hasXml(domElement) && !(domElement.getParent() instanceof DomFileElement)) {
        enabled = true;
      }
    }

    e.getPresentation().setEnabled(enabled);


    if (enabled) {
      e.getPresentation().setText(getPresentationText(selectedNode, ApplicationBundle.message("action.remove")));
    }
    else {
      e.getPresentation().setText(ApplicationBundle.message("action.remove"));
    }

    e.getPresentation().setIcon(AllIcons.General.Remove);
  }

  private static String getPresentationText(final SimpleNode selectedNode, String removeString) {
    final ElementPresentation presentation = ((BaseDomElementNode)selectedNode).getDomElement().getPresentation();
    removeString += " " + presentation.getTypeName() +
                                (presentation.getElementName() == null || presentation.getElementName().trim().length() == 0? "" : ": " + presentation.getElementName());
    return removeString;
  }
}
