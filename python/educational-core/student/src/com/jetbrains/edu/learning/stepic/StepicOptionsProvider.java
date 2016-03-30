package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.options.ConfigurationException;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StepicOptionsProvider implements StudyOptionsProvider{

  private StepicStudyOptions myPanel;

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanel = new StepicStudyOptions();
    return myPanel.getPanel();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
  }
}
