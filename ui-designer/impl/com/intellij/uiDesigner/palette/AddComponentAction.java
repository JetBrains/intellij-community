/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/**
 * @author yole
 */
public class AddComponentAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;
    GroupItem groupItem = (GroupItem)e.getDataContext().getData(GroupItem.class.getName());
    PsiFile psiFile = (PsiFile)e.getDataContext().getData(DataConstants.PSI_FILE);
    PsiElement elementToAdd = (psiFile != null) ? findElementToAdd(psiFile) : null;
    String className = "";
    if (elementToAdd instanceof PsiClass) {
      className = ((PsiClass) elementToAdd).getQualifiedName();
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
      new HashMap<String, StringDescriptor>(),
      true/*all user defined components are removable*/,
      false,
      false
    );
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    final ComponentItemDialog dialog = new ComponentItemDialog(project, parentWindow, itemToBeAdded, false);
    dialog.setTitle(UIDesignerBundle.message("title.add.component"));
    dialog.showGroupChooser(groupItem);
    dialog.show();
    if(!dialog.isOK()){
      return;
    }

    groupItem = dialog.getSelectedGroup();
    // If the itemToBeAdded is already in palette do nothing
    if(groupItem.containsItemClass(itemToBeAdded.getClassName())){
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
      PsiClass aClass = PsiManager.getInstance(project).findClass(itemToBeAdded.getClassName().replace('$', '.'), project.getAllScope());
      while(aClass != null) {
        final ComponentItem item = palette.getItem(aClass.getQualifiedName());
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

  @Override public void update(AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if (e.getDataContext().getData(GroupItem.class.getName()) != null ||
        e.getDataContext().getData(ComponentItem.class.getName()) != null) {
      e.getPresentation().setVisible(true);
      GroupItem groupItem = (GroupItem)e.getDataContext().getData(GroupItem.class.getName());
      e.getPresentation().setEnabled(project != null && (groupItem == null || !groupItem.isReadOnly()));
    }
    else {
      PsiFile psiFile = (PsiFile)e.getDataContext().getData(DataConstants.PSI_FILE);
      e.getPresentation().setVisible(psiFile != null && findElementToAdd(psiFile) != null);
    }
  }

  @Nullable
  private static PsiElement findElementToAdd(final PsiFile psiFile) {
    if (psiFile.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      return psiFile;
    }
    else if (psiFile.getFileType().equals(StdFileTypes.JAVA)) {
      final PsiClass psiClass = PsiTreeUtil.getChildOfType(psiFile, PsiClass.class);
      Project project = psiFile.getProject();
      final PsiClass componentClass = PsiManager.getInstance(project).findClass(JComponent.class.getName(), project.getAllScope());
      if (psiClass != null && componentClass != null && psiClass.isInheritor(componentClass, true) && psiClass.getQualifiedName() != null) {
        return psiClass;
      }
    }
    return null;
  }
}
