package com.jetbrains.edu.coursecreator.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PyCCRunTestsConfigurationType implements ConfigurationType {
  @Override
  public String getDisplayName() {
    return "Run Study Tests";
  }

  @Override
  public String getConfigurationTypeDescription() {
    return "Study Test Runner";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Actions.Lightning;
  }

  @NotNull
  @Override
  public String getId() {
    return "ccruntests";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{new PyCCRunTestsConfigurationFactory(this)};
  }

  public static PyCCRunTestsConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PyCCRunTestsConfigurationType.class);
  }
}
