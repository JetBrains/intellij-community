package com.intellij.tasks.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"UnusedDeclaration"})
public class TaskConfigurable extends BindableConfigurable implements SearchableConfigurable.Parent,
                                                                      NonDefaultProjectConfigurable {
  
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
        enableFields();
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

  private void enableFields() {
    GuiUtils.enableChildren(myCacheSettings, myUpdateCheckBox.isSelected());
  }

  @Override
  public void reset() {
    super.reset();
    enableFields();
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean old = getConfig().updateEnabled;
    super.apply();
    if (getConfig().updateEnabled && !old) {
      TaskManager.getManager(myProject).updateIssues(null);
    }
  }

  @Nls
  public String getDisplayName() {
    return "Tasks";
  }

  public Icon getIcon() {
    return null;
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

  public Runnable enableSearch(String option) {
    return null;
  }

  public boolean hasOwnContent() {
    return true;
  }

  public boolean isVisible() {
    return true;
  }

  public Configurable[] getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = new Configurable[] { new TaskRepositoriesConfigurable(myProject) };
    }
    return myConfigurables;
  }
}
