/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopupStep;

import java.util.Collections;
import java.util.List;

public class BaseTreePopupStep<T> extends BaseStep<T> implements TreePopupStep<T> {

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

  public boolean isSelectable(T node, T userData) {
    return true;
  }

  public boolean hasSubstep(T selectedValue) {
    return false;
  }

  public PopupStep onChosen(T selectedValue) {
    return FINAL_CHOICE;
  }

  public void canceled() {
  }

  public String getTextFor(Object value) {
    return value.toString();
  }

  public List<T> getValues() {
    return Collections.emptyList();
  }

  public boolean isSpeedSearchEnabled() {
    return true;
  }

}
