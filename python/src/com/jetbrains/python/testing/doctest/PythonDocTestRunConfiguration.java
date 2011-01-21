package com.jetbrains.python.testing.doctest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PythonDocTestRunConfiguration extends AbstractPythonTestRunConfiguration
                                          implements PythonDocTestRunConfigurationParams {
  private String myPattern = ""; // pattern for modules in folder to match against
  protected String myTitle = "Doctests";
  protected PythonDocTestRunConfiguration(RunConfigurationModule module, ConfigurationFactory configurationFactory, String name) {
    super(module, configurationFactory, name);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new PythonDocTestRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonDocTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonDocTestCommandLineState(this, env);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myPattern = JDOMExternalizerUtil.readField(element, "PATTERN");
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "PATTERN", myPattern);
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  public static void copyParams(PythonDocTestRunConfigurationParams source, PythonDocTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
    target.setPattern(source.getPattern());
  }

  public String getPattern() {
    return myPattern;
  }

  public void setPattern(String pattern) {
    myPattern = pattern;
  }
}
