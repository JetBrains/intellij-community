/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopupStep;

public class BaseTreePopupStep extends BaseStep implements TreePopupStep {

  private final String myTitle;
  private final AbstractTreeStructure myStructure;
  private final Project myProject;

  public BaseTreePopupStep(Project project, String aTitle, AbstractTreeStructure aStructure) {
    myTitle = aTitle;
    myStructure = aStructure;
    myProject = project;
  }

  public AbstractTreeStructure getStructure() {
    return myStructure;
  }

  public boolean isRootVisible() {
    return false;
  }

  public Project getProject() {
    return myProject;
  }

  public String getTitle() {
    return myTitle;
  }

  public boolean isSelectable(Object node, Object userData) {
    return true;
  }

  public boolean hasSubstep(Object selectedValue) {
    return false;
  }

  public PopupStep onChosen(Object selectedValue) {
    return FINAL_CHOICE;
  }

  public void canceled() {
  }

  public String getTextFor(Object value) {
    return value.toString();
  }

  public Object[] getValues() {
    return new Object[0];
  }

  public final boolean isMnemonicsNavigationEnabled() {
    return false;
  }

  public boolean isSpeedSearchEnabled() {
    return true;
  }

}
