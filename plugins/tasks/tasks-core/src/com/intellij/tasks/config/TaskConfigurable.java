// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.config;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.tasks.CommitPlaceholderProvider;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.impl.BaseRepositoryImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ExtendableEditorSupport;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"UnusedDeclaration"})
public class TaskConfigurable extends BindableConfigurable implements SearchableConfigurable.Parent, Configurable.NoScroll {
  private JPanel myPanel;

  @BindControl("updateEnabled")
  private JCheckBox myUpdateCheckBox;

  @BindControl("updateIssuesCount")
  private JTextField myUpdateCount;

  @BindControl("updateInterval")
  private JTextField myUpdateInterval;

  @BindControl("taskHistoryLength")
  private JTextField myHistoryLength;
  private JPanel myCacheSettings;

  @BindControl("saveContextOnCommit")
  private JCheckBox mySaveContextOnCommit;

  @BindControl("changelistNameFormat")
  private EditorTextField myChangelistNameFormat;

  private JBCheckBox myAlwaysDisplayTaskCombo;
  private JTextField myConnectionTimeout;

  @BindControl("branchNameFormat")
  private EditorTextField myBranchNameFormat;
  private JCheckBox myLowerCase;
  private JBTextField myReplaceSpaces;

  private final Project myProject;
  private Configurable[] myConfigurables;
  private final NotNullLazyValue<ControlBinder> myControlBinder;

  public TaskConfigurable(Project project) {
    super();
    myProject = project;
    myUpdateCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        enableCachePanel();
      }
    });
    myControlBinder = NotNullLazyValue.lazy(() -> new ControlBinder(getConfig()));
  }

  private TaskManagerImpl.Config getConfig() {
    return ((TaskManagerImpl)TaskManager.getManager(myProject)).getState();
  }

  @Override
  protected ControlBinder getBinder() {
    return myControlBinder.getValue();
  }

  private void enableCachePanel() {
    myUpdateCount.setEnabled(myUpdateCheckBox.isSelected());
    myUpdateInterval.setEnabled(myUpdateCheckBox.isSelected());
  }

  @Override
  public void reset() {
    super.reset();
    enableCachePanel();
    myAlwaysDisplayTaskCombo.setSelected(TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO);
    myConnectionTimeout.setText(Integer.toString(TaskSettings.getInstance().CONNECTION_TIMEOUT));
    myLowerCase.setSelected(TaskSettings.getInstance().LOWER_CASE_BRANCH);
    myReplaceSpaces.setText(TaskSettings.getInstance().REPLACE_SPACES);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myChangelistNameFormat.getText().trim().isEmpty()) {
      throw new ConfigurationException(TaskBundle.message("settings.change.list.name.format.should.not.be.empty"));
    }
    if (myBranchNameFormat.getText().trim().isEmpty()) {
      throw new ConfigurationException(TaskBundle.message("settings.Branch.name.format.should.not.be.empty"));
    }
    boolean oldUpdateEnabled = getConfig().updateEnabled;
    super.apply();
    TaskManager manager = TaskManager.getManager(myProject);
    if (getConfig().updateEnabled && !oldUpdateEnabled) {
      manager.updateIssues(null);
    }
    TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = myAlwaysDisplayTaskCombo.isSelected();
    int oldConnectionTimeout = TaskSettings.getInstance().CONNECTION_TIMEOUT;
    int connectionTimeout = Integer.parseInt(myConnectionTimeout.getText());
    TaskSettings.getInstance().CONNECTION_TIMEOUT = connectionTimeout;
    TaskSettings.getInstance().LOWER_CASE_BRANCH = myLowerCase.isSelected();
    TaskSettings.getInstance().REPLACE_SPACES = myReplaceSpaces.getText();

    if (connectionTimeout != oldConnectionTimeout) {
      for (TaskRepository repository : manager.getAllRepositories()) {
        if (repository instanceof BaseRepositoryImpl) {
          ((BaseRepositoryImpl)repository).reconfigureClient();
        }
      }
    }
  }

  @Override
  public boolean isModified() {
    return super.isModified() ||
           TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO != myAlwaysDisplayTaskCombo.isSelected() ||
           TaskSettings.getInstance().CONNECTION_TIMEOUT != Integer.parseInt(myConnectionTimeout.getText()) ||
           TaskSettings.getInstance().LOWER_CASE_BRANCH != myLowerCase.isSelected() ||
           !Objects.equals(TaskSettings.getInstance().REPLACE_SPACES, myReplaceSpaces.getText());
  }

  @Override
  public String getDisplayName() {
    return TaskBundle.message("configurable.TaskConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.tasks";
  }

  @Override
  public JComponent createComponent() {
    bindAnnotations();
    return myPanel;
  }

  @Override
  @NotNull
  public String getId() {
    return "tasks";
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = new Configurable[] { new TaskRepositoriesConfigurable(myProject) };
    }
    return myConfigurables;
  }

  private void createUIComponents() {
    FileType fileType = FileTypeManager.getInstance().findFileTypeByName("VTL");
    if (fileType == null) {
      fileType = PlainTextFileType.INSTANCE;
    }
    Project project = ProjectManager.getInstance().getDefaultProject();
    myBranchNameFormat = new EditorTextField(project, fileType);
    setupAddAction(myBranchNameFormat);
    myChangelistNameFormat = new EditorTextField(project, fileType);
    setupAddAction(myChangelistNameFormat);
  }

  private void setupAddAction(EditorTextField field) {
    field.addSettingsProvider(editor -> {
      ExtendableTextComponent.Extension extension =
        ExtendableTextComponent.Extension
          .create(AllIcons.General.InlineAdd, AllIcons.General.InlineAddHover, TaskBundle.message("settings.add.placeholder"), () -> {
          Set<String> placeholders = new HashSet<>();
          for (CommitPlaceholderProvider provider : CommitPlaceholderProvider.EXTENSION_POINT_NAME.getExtensionList()) {
            placeholders.addAll(Arrays.asList(provider.getPlaceholders(null)));
          }
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(TaskBundle.message("settings.placeholders"),
                                                                               ArrayUtilRt.toStringArray(placeholders)) {
            @Override
            public PopupStep onChosen(String selectedValue, boolean finalChoice) {
              WriteCommandAction.runWriteCommandAction(myProject, () -> editor.getDocument()
                .insertString(editor.getCaretModel().getOffset(), "${" + selectedValue + "}"));
              return FINAL_CHOICE;
            }
          }).showInBestPositionFor(editor);
        });
      ExtendableEditorSupport.setupExtension(editor, field.getBackground(), extension);
    });
  }
}
