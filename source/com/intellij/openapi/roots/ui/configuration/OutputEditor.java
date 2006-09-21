/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 12-Aug-2006
 * Time: 20:14:02
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class OutputEditor extends ModuleElementsEditor {
  private BuildElementsEditor myCompilerOutputEditor;
  private JavadocEditor myJavadocEditor;

  protected OutputEditor(final Project project, final ModifiableRootModel model) {
    super(project, model);
    myCompilerOutputEditor = new BuildElementsEditor(project, model);
    myJavadocEditor = new JavadocEditor(project, model);
  }

  protected JComponent createComponentImpl() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myCompilerOutputEditor.createComponentImpl(), BorderLayout.NORTH);
    final JPanel javadocPanel = (JPanel)myJavadocEditor.createComponentImpl();
    javadocPanel.setBorder(BorderFactory.createTitledBorder(myJavadocEditor.getDisplayName()));
    panel.add(javadocPanel, BorderLayout.CENTER);
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    return panel;
  }

  public void saveData() {
    myCompilerOutputEditor.saveData();
    myJavadocEditor.saveData();
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.output.and.javadoc.tab.title");
  }

  public Icon getIcon() {
    return myCompilerOutputEditor.getIcon();
  }


  public void moduleStateChanged() {
    super.moduleStateChanged();
    myCompilerOutputEditor.moduleStateChanged();
    myJavadocEditor.moduleStateChanged();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.paths.javadoc";
  }
}