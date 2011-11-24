package com.jetbrains.python.testing.attest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PythonAtTestRunConfiguration extends AbstractPythonTestRunConfiguration
                                          implements PythonAtTestRunConfigurationParams {
  protected String myTitle = "Attests";
  public PythonAtTestRunConfiguration(RunConfigurationModule module,
                                      ConfigurationFactory configurationFactory,
                                      String name) {
    super(module, configurationFactory, name);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new PythonAtTestRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  @Override
  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    return new PythonAtTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonAtTestCommandLineState(this, env);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  public static void copyParams(PythonAtTestRunConfigurationParams source, PythonAtTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (!VFSTestFrameworkListener.getInstance().isAtTestInstalled(getSdkHome()))
      throw new RuntimeConfigurationWarning("No attest runner found in selected interpreter");
  }
}
