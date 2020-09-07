// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;

import javax.swing.*;
import java.util.Collection;

public class MavenEditGoalDialog extends DialogWrapper {

  private final Project myProject;
  @Nullable private final Collection<String> myHistory;

  private JPanel contentPane;

  private FixedSizeButton showProjectTreeButton;
  private TextFieldWithBrowseButton workDirectoryField;

  private JPanel goalsPanel;
  private JLabel goalsLabel;
  private ComboBox goalsComboBox;
  private EditorTextField goalsEditor;


  public MavenEditGoalDialog(@NotNull Project project) {
    this(project, null);
  }

  public MavenEditGoalDialog(@NotNull Project project, @Nullable Collection<String> history) {
    super(project, true);
    myProject = project;
    myHistory = history;

    setTitle(TasksBundle.message("maven.tasks.goal.edit"));
    setUpDialog();
    setModal(true);
    init();
  }

  private void setUpDialog() {
    JComponent goalComponent;
    if (myHistory == null) {
      goalsEditor = new EditorTextField("", myProject, PlainTextFileType.INSTANCE);
      goalComponent = goalsEditor;

      goalsLabel.setLabelFor(goalsEditor);
    }
    else {
      goalsComboBox = new ComboBox(ArrayUtilRt.toStringArray(myHistory));
      goalComponent = goalsComboBox;

      goalsLabel.setLabelFor(goalsComboBox);

      goalsComboBox.setLightWeightPopupEnabled(false);

      EditorComboBoxEditor editor = new StringComboboxEditor(myProject, PlainTextFileType.INSTANCE, goalsComboBox);
      goalsComboBox.setRenderer(new EditorComboBoxRenderer(editor));

      goalsComboBox.setEditable(true);
      goalsComboBox.setEditor(editor);
      goalsComboBox.setFocusable(true);

      goalsEditor = editor.getEditorComponent();
    }

    goalsPanel.add(goalComponent);

    new MavenArgumentsCompletionProvider(myProject).apply(goalsEditor);


    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);

    showProjectTreeButton.setIcon(AllIcons.Nodes.Module);
    MavenSelectProjectPopup.attachToWorkingDirectoryField(projectsManager, workDirectoryField.getTextField(), showProjectTreeButton,
                                                          goalsComboBox != null ? goalsComboBox : goalsEditor);

    workDirectoryField.addBrowseFolderListener(
      RunnerBundle.message("maven.select.working.directory"), "", myProject,
      new MavenPomFileChooserDescriptor(myProject));
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (workDirectoryField.getText().trim().isEmpty()) {
      return new ValidationInfo(TasksBundle.message("maven.tasks.edit.working.dir.is.empty"), workDirectoryField);
    }

    return null;
  }

  @NotNull
  public String getGoals() {
    if (goalsComboBox != null) {
      return (String)goalsComboBox.getEditor().getItem();
    }
    else {
      return goalsEditor.getText();
    }
  }

  public void setGoals(@NotNull @NlsSafe String goals) {
    if (goalsComboBox != null) {
      goalsComboBox.setSelectedItem(goals);
    }

    goalsEditor.setText(goals);
  }

  @NotNull
  public String getWorkDirectory() {
    return workDirectoryField.getText();
  }

  public void setWorkDirectory(@NotNull @NlsSafe String path) {
    workDirectoryField.setText(path);
  }

  public void setSelectedMavenProject(@Nullable MavenProject mavenProject) {
    workDirectoryField.setText(mavenProject == null ? "" : mavenProject.getDirectory());
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return goalsComboBox;
  }

}
