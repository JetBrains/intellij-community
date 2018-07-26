/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.RadPropertyTable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ObjectUtils;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class ShowJavadoc extends AnAction implements IPropertyTableAction {
  private final RadPropertyTable myTable;

  public ShowJavadoc(RadPropertyTable table) {
    myTable = table;

    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.show.javadoc");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(AllIcons.Actions.Help);
  }

  @Override
  public void update(AnActionEvent e) {
    setEnabled(myTable, e, e.getPresentation());
  }

  @Override
  public void update() {
    setEnabled(myTable, null, getTemplatePresentation());
  }

  private static void setEnabled(RadPropertyTable table, AnActionEvent e, Presentation presentation) {
    Property property = table.getSelectionProperty();
    presentation.setEnabled(property != null &&
                            !table.isEditing() &&
                            (property.getJavadocElement() != null || !StringUtil.isEmpty(property.getJavadocText())) &&
                            (e == null || e.getProject() != null));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    final DocumentationComponent component = new DocumentationComponent(documentationManager);

    final Property property = myTable.getSelectionProperty();
    if (property == null) {
      return;
    }

    PsiElement javadocElement = property.getJavadocElement();

    ActionCallback callback;
    if (javadocElement == null) {
      callback = new ActionCallback();
      component.setText(ObjectUtils.notNull(property.getJavadocText()), null, null);
      component.clearHistory();
    }
    else {
      callback = documentationManager.queueFetchDocInfo(javadocElement, component);
    }

    callback.doWhenProcessed(() -> {
      JBPopup hint =
        JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
          .setProject(project)
          .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
          .setResizable(true)
          .setMovable(true)
          .setRequestFocus(true)
          .setTitle(DesignerBundle.message("designer.properties.javadoc.title", property.getName()))
          .setCancelCallback(() -> {
            Disposer.dispose(component);
            return Boolean.TRUE;
          })
          .createPopup();
      component.setHint(hint);
      Disposer.register(hint, component);
      hint.show(new RelativePoint(myTable.getParent(), new Point(0, 0)));
    });

    if (javadocElement == null) {
      callback.setDone();
    }
  }
}
