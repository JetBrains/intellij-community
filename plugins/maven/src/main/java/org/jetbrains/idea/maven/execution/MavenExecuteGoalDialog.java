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
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;

public class MavenExecuteGoalDialog extends DialogWrapper {

  private final Project myProject;

  private JPanel contentPane;
  private ComboBox myGoalsComboBox;
  private FixedSizeButton showProjectTreeButton;
  private TextFieldWithBrowseButton pomXmlTextField;

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
        pomXmlTextField.setText(project.getPath());
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
  public String getPomXmlPath() {
    return pomXmlTextField.getText();
  }

  public void setSelectedMavenProject(@Nullable MavenProject mavenProject) {
    pomXmlTextField.setText(mavenProject == null ? "" : mavenProject.getPath());
  }

  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getPreferredFocusedComponent() {
    return myGoalsComboBox;
  }

  //private void createUIComponents() {
  //  //myCmdField = new ComboBox(MvcRunTargetHistoryService.getInstance().getHistory(), -1);
  //  myCmdField = new ComboBox(new Object[0], -1);
  //  myCmdField.setLightWeightPopupEnabled(false);
  //
  //  EditorComboBoxEditor editor = new StringComboboxEditor(myProject, PlainTextFileType.INSTANCE, myCmdField);
  //  myCmdField.setRenderer(new EditorComboBoxRenderer(editor));
  //
  //  myCmdField.setEditable(true);
  //  myCmdField.setEditor(editor);
  //
  //  EditorTextField editorTextField = editor.getEditorComponent();
  //
  //  myFakePanel = new JPanel(new BorderLayout());
  //  myFakePanel.add(myCmdField, BorderLayout.CENTER);
  //
  //  //TextFieldCompletionProvider vmOptionCompletionProvider = new TextFieldCompletionProviderDumbAware() {
  //  //  @NotNull
  //  //  @Override
  //  //  protected String getPrefix(@NotNull String currentTextPrefix) {
  //  //    return MavenExecuteGoalDialog.getPrefix(currentTextPrefix);
  //  //  }
  //  //
  //  //  @Override
  //  //  protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
  //  //    if (prefix.endsWith("-D")) {
  //  //      result.addAllElements(MvcTargetDialogCompletionUtils.getSystemPropertiesVariants());
  //  //    }
  //  //  }
  //  //};
  //  //myVmOptionsField = vmOptionCompletionProvider.createEditor(myModule.getProject());
  //
  //  new TextFieldCompletionProviderDumbAware() {
  //    //@NotNull
  //    //@Override
  //    //protected String getPrefix(@NotNull String currentTextPrefix) {
  //    //  return MavenExecuteGoalDialog.getPrefix(currentTextPrefix);
  //    //}
  //
  //    @Override
  //    protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
  //      //for (LookupElement variant : MvcTargetDialogCompletionUtils.collectVariants(myModule, text, offset, prefix)) {
  //      //  result.addElement(variant);
  //      //}
  //    }
  //  }.apply(editorTextField);
  //
  //}

}
