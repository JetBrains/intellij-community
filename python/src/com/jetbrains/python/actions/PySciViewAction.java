/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.console.PythonConsoleToolWindow;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.codeInsight.documentation.DocumentationComponent.COLOR_KEY;
import static com.jetbrains.python.debugger.containerview.PyDataView.DATA_VIEWER_ID;

public class PySciViewAction extends ToggleAction implements DumbAware {
  private static final String TEXT_SCI_VIEW = "Scientific Mode";

  private boolean wasConsoleVisible = false;
  private ToolWindowType myDocumentationType = ToolWindowType.FLOATING;
  private boolean myDocumentationAutoHide = true;

  public static final String ACTION_ID = "PySciView";
  private ToolWindowType myDataViewType = ToolWindowType.FLOATING;
  private Color myDocumentationBackgroundColor = EditorColorsManager.getInstance().getGlobalScheme().getColor(COLOR_KEY);

  public PySciViewAction() {
    super(TEXT_SCI_VIEW);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return e.getProject() != null && PySciProjectComponent.getInstance(e.getProject()).useSciView();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Project project = e.getProject();
    if (project == null) return;
    PySciProjectComponent.getInstance(project).useSciView(state);

    final PsiElement element = getPsiElement(e, project);

    if (state) {
      showConsoleToolwindow(project);
      showDocumentationToolwindow(project, element);
      showDataViewAsToolwindow(project);
    }
    else {
      hideConsoleToolwindow(project);
      restoreDocumentationPopup(project);
      hideDataViewer(project);
    }
  }

  private void showConsoleToolwindow(@NotNull final Project project) {
    final ToolWindow consoleToolWindow = PythonConsoleToolWindow.getInstance(project).getToolWindow();
    wasConsoleVisible = consoleToolWindow.isVisible();
    consoleToolWindow.show(null);
  }

  private void hideConsoleToolwindow(@NotNull final Project project) {
    if (!wasConsoleVisible) {
      PythonConsoleToolWindow.getInstance(project).getToolWindow().hide(null);
    }
  }

  private void showDocumentationToolwindow(Project project, PsiElement element) {
    final String showInToolWindowProperty = DocumentationManager.getInstance(project).getShowInToolWindowProperty();

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myDocumentationBackgroundColor = scheme.getColor(COLOR_KEY);
    scheme.setColor(COLOR_KEY, UIUtil.getEditorPaneBackground());

    PropertiesComponent.getInstance().setValue(showInToolWindowProperty, true);
    PropertiesComponent.getInstance().setValue(DocumentationManager.getInstance(project).getAutoUpdateEnabledProperty(), true);

    if (element != null) {
      DocumentationManager.getInstance(project).showJavaDocInfo(element, element);
    }
    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
    if (window != null) {
      window.setAnchor(ToolWindowAnchor.RIGHT, null);
      myDocumentationType = window.getType();
      myDocumentationAutoHide = window.isAutoHide();
      window.setType(ToolWindowType.DOCKED, null);
      window.setAutoHide(false);
    }
  }

  private void restoreDocumentationPopup(Project project) {
    PropertiesComponent.getInstance().setValue(DocumentationManager.getInstance(project).getAutoUpdateEnabledProperty(), false);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    scheme.setColor(COLOR_KEY, myDocumentationBackgroundColor);

    final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
    if (window != null) {
      window.setType(myDocumentationType, null);
      window.setAutoHide(myDocumentationAutoHide);
      DocumentationManager.getInstance(project).restorePopupBehavior();
      final JBPopup hint = DocumentationManager.getInstance(project).getDocInfoHint();
      if (hint != null) {
        hint.cancel();
      }
    }
  }

  private void showDataViewAsToolwindow(@NotNull final Project project) {
    ToolWindow dataViewToolWindow = ToolWindowManager.getInstance(project).getToolWindow(DATA_VIEWER_ID);
    myDataViewType = dataViewToolWindow.getType();
    dataViewToolWindow.setType(ToolWindowType.DOCKED, null);
    dataViewToolWindow.setAutoHide(false);
    dataViewToolWindow.setShowStripeButton(true);
  }

  private void hideDataViewer(Project project) {
    final ToolWindow dataViewer = ToolWindowManager.getInstance(project).getToolWindow(DATA_VIEWER_ID);
    dataViewer.setType(myDataViewType, null);
    dataViewer.setAutoHide(true);
    dataViewer.setShowStripeButton(false);
  }

  private static PsiElement getPsiElement(AnActionEvent e, Project project) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(e.getDataContext());
    if (element == null) {
      element = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    }
    if (element == null) {
      element = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
    }
    return element;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final Project project = e.getProject();
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null);
  }
}
