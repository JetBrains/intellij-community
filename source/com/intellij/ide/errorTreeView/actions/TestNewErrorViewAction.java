/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView.actions;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 13, 2004
 */
public class TestNewErrorViewAction extends TestErrorViewAction{
  protected ErrorTreeView createView(Project project) {
    return new NewErrorTreeViewPanel(project, null);
  }

  @NonNls
  protected String getContentName() {
    return "NewView";
  }
}
