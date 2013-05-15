/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;

public class MavenExecuteGoalDialog extends DialogWrapper {

  private final Project myProject;

  private JPanel contentPane;
  private ComboBox myGoalsComboBox;
  private FixedSizeButton showProjectTreeButton;
  private TextFieldWithBrowseButton workDirectoryField;

  public MavenExecuteGoalDialog(@NotNull Project project) {
    super(project, true);
    myProject = project;

    setTitle("Run Maven Goal");
    setUpDialog();
    setModal(true);
    init();
  }

  private void setUpDialog() {
    // Configure project combobox
    myGoalsComboBox.setLightWeightPopupEnabled(false);

    EditorComboBoxEditor editor = new StringComboboxEditor(myProject, PlainTextFileType.INSTANCE, myGoalsComboBox);
    myGoalsComboBox.setRenderer(new EditorComboBoxRenderer(editor));

    myGoalsComboBox.setEditable(true);
    myGoalsComboBox.setEditor(editor);
    myGoalsComboBox.setFocusable(true);

    EditorTextField editorTextField = editor.getEditorComponent();

    new MavenArgumentsCompletionProvider(myProject).apply(editorTextField);

    // Configure Module ComboBox
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(myProject);

    showProjectTreeButton.setIcon(AllIcons.Actions.Module);
    MavenSelectProjectPopup.attachToButton(showProjectTreeButton, projectsManager, new Consumer<MavenProject>() {
      @Override
      public void consume(MavenProject project) {
        workDirectoryField.setText(project.getDirectory());
      }
    });

    MavenSelectProjectPopup.clickButtonOnDown(workDirectoryField.getTextField(), showProjectTreeButton);

    workDirectoryField.addBrowseFolderListener(
      RunnerBundle.message("maven.select.maven.project.file"), "", myProject,
      new FileChooserDescriptor(false, true, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          if (!super.isFileSelectable(file)) return false;
          return file.findChild(MavenConstants.POM_XML) != null;
        }
      });
  }

  @NotNull
  public String getGoals() {
    return (String)myGoalsComboBox.getEditor().getItem();
  }

  public void setGoals(@NotNull String goals) {
    myGoalsComboBox.setSelectedItem(goals);
  }

  @NotNull
  public String getWorkDirectory() {
    return workDirectoryField.getText();
  }

  public void setSelectedMavenProject(@Nullable MavenProject mavenProject) {
    workDirectoryField.setText(mavenProject == null ? "" : mavenProject.getDirectory());
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getPreferredFocusedComponent() {
    return myGoalsComboBox;
  }

}
