/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tasks.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
  private JTextField myChangelistNameFormat;

  private JBCheckBox myAlwaysDisplayTaskCombo;
  private JTextField myConnectionTimeout;

  @BindControl("branchNameFormat")
  private JTextField myBranchNameFormat;

  private final Project myProject;
  private Configurable[] myConfigurables;
  private final NotNullLazyValue<ControlBinder> myControlBinder = new NotNullLazyValue<ControlBinder>() {
    @NotNull
    @Override
    protected ControlBinder compute() {
      return new ControlBinder(getConfig());
    }
  };

  public TaskConfigurable(Project project) {
    super();
    myProject = project;
    myUpdateCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableCachePanel();
      }
    });
  }

  private TaskManagerImpl.Config getConfig() {
    return ((TaskManagerImpl)TaskManager.getManager(myProject)).getState();
  }

  @Override
  protected ControlBinder getBinder() {
    return myControlBinder.getValue();
  }

  private void enableCachePanel() {
    GuiUtils.enableChildren(myCacheSettings, myUpdateCheckBox.isSelected());
  }

  @Override
  public void reset() {
    super.reset();
    enableCachePanel();
    myAlwaysDisplayTaskCombo.setSelected(TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO);
    myConnectionTimeout.setText(Integer.toString(TaskSettings.getInstance().CONNECTION_TIMEOUT));
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean oldUpdateEnabled = getConfig().updateEnabled;
    super.apply();
    TaskManager manager = TaskManager.getManager(myProject);
    if (getConfig().updateEnabled && !oldUpdateEnabled) {
      manager.updateIssues(null);
    }
    TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO = myAlwaysDisplayTaskCombo.isSelected();
    int oldConnectionTimeout = TaskSettings.getInstance().CONNECTION_TIMEOUT;
    Integer connectionTimeout = Integer.valueOf(myConnectionTimeout.getText());
    TaskSettings.getInstance().CONNECTION_TIMEOUT = connectionTimeout;

    if (manager instanceof TaskManagerImpl && connectionTimeout != oldConnectionTimeout) {
      ((TaskManagerImpl)manager).reconfigureRepositoryClients();
    }
  }

  @Override
  public boolean isModified() {
    return super.isModified() ||
           TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO != myAlwaysDisplayTaskCombo.isSelected() ||
      TaskSettings.getInstance().CONNECTION_TIMEOUT != Integer.valueOf(myConnectionTimeout.getText());
  }

  @Nls
  public String getDisplayName() {
    return "Tasks";
  }

  public String getHelpTopic() {
    return "reference.settings.project.tasks";
  }

  public JComponent createComponent() {
    bindAnnotations();
    return myPanel;
  }

  public void disposeUIResources() {
  }

  @NotNull
  public String getId() {
    return "tasks";
  }

  public boolean hasOwnContent() {
    return true;
  }

  public Configurable[] getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = new Configurable[] { new TaskRepositoriesConfigurable(myProject) };
    }
    return myConfigurables;
  }
}
