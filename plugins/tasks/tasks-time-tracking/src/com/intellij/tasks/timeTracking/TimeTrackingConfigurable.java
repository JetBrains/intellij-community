package com.intellij.tasks.timeTracking;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.binding.BindControl;
import com.intellij.openapi.options.binding.BindableConfigurable;
import com.intellij.openapi.options.binding.ControlBinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: Evgeny.Zakrevsky
 * Date: 11/19/12
 */
public class TimeTrackingConfigurable extends BindableConfigurable implements SearchableConfigurable,
                                                                              NonDefaultProjectConfigurable, Configurable.NoScroll {
  @BindControl("enabled")
  private JCheckBox myEnableTimeTrackingCheckBox;
  @BindControl("suspendDelayInSeconds")
  private JTextField myTimeTrackingSuspendDelay;
  private JPanel myTimeTrackingSettings;
  private JPanel myPanel;
  private Project myProject;
  private final NotNullLazyValue<ControlBinder> myControlBinder = new NotNullLazyValue<ControlBinder>() {
    @NotNull
    @Override
    protected ControlBinder compute() {
      return new ControlBinder(getConfig());
    }
  };


  public TimeTrackingConfigurable(Project project) {
    myProject = project;
    myEnableTimeTrackingCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableTimeTrackingPanel();
      }
    });
  }

  private void enableTimeTrackingPanel() {
    GuiUtils.enableChildren(myTimeTrackingSettings, myEnableTimeTrackingCheckBox.isSelected());
  }

  private TimeTrackingManager.Config getConfig() {
    return TimeTrackingManager.getInstance(myProject).getState();
  }

  @Override
  protected ControlBinder getBinder() {
    return myControlBinder.getValue();
  }

  @Override
  public void reset() {
    super.reset();
    enableTimeTrackingPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean oldTimeTrackingEnabled = getConfig().enabled;
    super.apply();
    if (getConfig().enabled != oldTimeTrackingEnabled) {
      TimeTrackingManager.getInstance(myProject).updateTimeTrackingToolWindow();
    }
  }

  @NotNull
  @Override
  public String getId() {
    return "tasks.timeTracking";
  }

  @Nullable
  @Override
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Time Tracking";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
    //return "reference.settings.project.tasks.timeTracking";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    bindAnnotations();
    return myPanel;
  }
}
