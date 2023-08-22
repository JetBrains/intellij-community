// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.palette;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;


public class AddComponentAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    PsiElement elementToAdd = (psiFile != null) ? findElementToAdd(psiFile) : null;
    String className = "";
    if (elementToAdd instanceof PsiClass) {
      className = ((PsiClass)elementToAdd).getQualifiedName();
      assert className != null;
    }
    else if (elementToAdd instanceof PsiFile) {
      try {
        className = Utils.getBoundClassName(elementToAdd.getText());
      }
      catch (Exception e1) {
        className = "";
      }
    }

    // Show dialog
    final ComponentItem itemToBeAdded = new ComponentItem(
      project,
      className,
      null,
      null,
      new GridConstraints(),
      new HashMap<>(),
      true/*all user defined components are removable*/,
      false,
      false
    );
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeAdded, false);
    dialog.setTitle(UIDesignerBundle.message("title.add.component"));
    dialog.showGroupChooser(groupItem);
    if (!dialog.showAndGet()) {
      return;
    }

    groupItem = dialog.getSelectedGroup();
    // If the itemToBeAdded is already in palette do nothing
    if (groupItem.containsItemClass(itemToBeAdded.getClassName())) {
      return;
    }

    assignDefaultIcon(project, itemToBeAdded);

    // add to the group

    final Palette palette = Palette.getInstance(project);
    palette.addItem(groupItem, itemToBeAdded);
    palette.fireGroupsChanged();
  }

  private static void assignDefaultIcon(final Project project, final ComponentItem itemToBeAdded) {
    Palette palette = Palette.getInstance(project);
    if (itemToBeAdded.getIconPath() == null || itemToBeAdded.getIconPath().length() == 0) {
      PsiClass aClass =
        JavaPsiFacade.getInstance(project).findClass(itemToBeAdded.getClassName().replace('$', '.'), ProjectScope.getAllScope(project));
      while (aClass != null) {
        String name = aClass.getQualifiedName();
        ComponentItem item = name == null ? null : palette.getItem(name);
        if (item != null) {
          String iconPath = item.getIconPath();
          if (iconPath != null && iconPath.length() > 0) {
            itemToBeAdded.setIconPath(iconPath);
            return;
          }
        }
        aClass = aClass.getSuperClass();
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (e.getData(GroupItem.DATA_KEY) != null ||
        e.getData(ComponentItem.DATA_KEY) != null) {
      e.getPresentation().setVisible(true);
      GroupItem groupItem = e.getData(GroupItem.DATA_KEY);
      e.getPresentation().setEnabled(project != null && (groupItem == null || !groupItem.isReadOnly()));
    }
    else {
      PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
      e.getPresentation().setVisible(psiFile != null && findElementToAdd(psiFile) != null);
    }
  }

  @Nullable
  private static PsiElement findElementToAdd(final PsiFile psiFile) {
    if (psiFile.getFileType().equals(GuiFormFileType.INSTANCE)) {
      return psiFile;
    }
    else if (psiFile.getFileType().equals(JavaFileType.INSTANCE)) {
      final PsiClass psiClass = PsiTreeUtil.getChildOfType(psiFile, PsiClass.class);
      Project project = psiFile.getProject();
      final PsiClass componentClass =
        JavaPsiFacade.getInstance(project).findClass(JComponent.class.getName(), ProjectScope.getAllScope(project));
      if (psiClass != null && componentClass != null && psiClass.isInheritor(componentClass, true) && psiClass.getQualifiedName() != null) {
        return psiClass;
      }
    }
    return null;
  }
}
