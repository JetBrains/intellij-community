/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 14-Aug-2006
 * Time: 12:13:18
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChooseModulesDialog extends ChooseElementsDialog<Module> {

  public ChooseModulesDialog(Component parent, final List<Module> items, final String title) {
    super(parent, items, title);
  }

  public ChooseModulesDialog(final Project project, final List<Module> items, final String title, final String description) {
    super(project, items, title, description);
  }

  protected Icon getItemIcon(final Module item) {
    return item.getModuleType().getNodeIcon(false);
  }

  protected String getItemText(final Module item) {
    return item.getName();
  }
}