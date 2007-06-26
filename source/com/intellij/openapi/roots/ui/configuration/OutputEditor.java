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
  private final BuildElementsEditor myCompilerOutputEditor;
  private final JavadocEditor myJavadocEditor;
  private final AnnotationsEditor myAnnotationsEditor;

  protected OutputEditor(final Project project, final ModifiableRootModel model) {
    super(project, model);
    myCompilerOutputEditor = new BuildElementsEditor(project, model);
    myJavadocEditor = new JavadocEditor(project, model);
    myAnnotationsEditor = new AnnotationsEditor(project, model);
  }

  protected JComponent createComponentImpl() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
    panel.add(myCompilerOutputEditor.createComponentImpl(), gc);
    final JPanel javadocPanel = (JPanel)myJavadocEditor.createComponentImpl();
    javadocPanel.setBorder(BorderFactory.createTitledBorder(myJavadocEditor.getDisplayName()));
    gc.gridy++;
    panel.add(javadocPanel, gc);
    final JPanel annotationsPanel = (JPanel)myAnnotationsEditor.createComponentImpl();
    annotationsPanel.setBorder(BorderFactory.createTitledBorder(myAnnotationsEditor.getDisplayName()));
    gc.gridy++;
    gc.weighty = 1.0;
    gc.fill = GridBagConstraints.BOTH;
    panel.add(annotationsPanel, gc);
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    return panel;
  }

  public void saveData() {
    myCompilerOutputEditor.saveData();
    myJavadocEditor.saveData();
    myAnnotationsEditor.saveData();
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.path.tab.title");
  }

  public Icon getIcon() {
    return myCompilerOutputEditor.getIcon();
  }


  public void moduleStateChanged() {
    super.moduleStateChanged();
    myCompilerOutputEditor.moduleStateChanged();
    myJavadocEditor.moduleStateChanged();
    myAnnotationsEditor.moduleStateChanged();
  }


  public void moduleCompileOutputChanged(final String baseUrl, final String moduleName) {
    super.moduleCompileOutputChanged(baseUrl, moduleName);
    myCompilerOutputEditor.moduleCompileOutputChanged(baseUrl, moduleName);
    myJavadocEditor.moduleCompileOutputChanged(baseUrl, moduleName);
    myAnnotationsEditor.moduleCompileOutputChanged(baseUrl, moduleName);
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "project.paths.javadoc";
  }
}