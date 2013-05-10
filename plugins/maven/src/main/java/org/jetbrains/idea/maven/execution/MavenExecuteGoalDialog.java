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

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProjectNamer;

import javax.swing.*;
import java.util.*;
import java.util.List;

public class MavenExecuteGoalDialog extends DialogWrapper {

  private final Project myProject;

  private JPanel contentPane;
  private JComboBox myModuleComboBox;
  private ComboBox myGoalsComboBox;

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

    List<MavenProject> mavenProjects = projectsManager.getProjects();

    final Map<MavenProject, String> nameMap = MavenProjectNamer.generateNameMap(mavenProjects);
    final Map<MavenProject, Integer> projectTreeMap = MavenProjectNamer.buildProjectTree(projectsManager);

    myModuleComboBox.setModel(new CollectionComboBoxModel(new ArrayList<MavenProject>(projectTreeMap.keySet())));

    myModuleComboBox.setRenderer(new ListCellRendererWrapper<MavenProject>() {
      @Override
      public void customize(JList list, MavenProject value, int index, boolean selected, boolean hasFocus) {
        String text = nameMap.get(value);

        Integer deep = projectTreeMap.get(value);

        if (deep != null && deep > 0) {
          text = StringUtil.repeat("  ", deep) + text;
        }

        setText(text);
      }
    });
    new ComboboxSpeedSearch(myModuleComboBox){
      protected String getElementText(Object element) {
        String name = nameMap.get((MavenProject)element);
        if (name != null) return name;

        if (element == null) return "";

        return ((MavenProject)element).getMavenId().toString();
      }
    }.setComparator(new SpeedSearchComparator(false, false));
  }

  @NotNull
  public String getGoals() {
    return (String)myGoalsComboBox.getEditor().getItem();
  }

  public void setGoals(@NotNull String goals) {
    myGoalsComboBox.setSelectedItem(goals);
  }

  @Nullable
  public MavenProject getSelectedMavenProject() {
    return (MavenProject)myModuleComboBox.getSelectedItem();
  }

  public void setSelectedMavenProject(@Nullable MavenProject mavenProject) {
    CollectionComboBoxModel model = (CollectionComboBoxModel)myModuleComboBox.getModel();

    //if (!model.contains(null)) {
    //  model.
    //}

    myModuleComboBox.setSelectedItem(mavenProject);
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
