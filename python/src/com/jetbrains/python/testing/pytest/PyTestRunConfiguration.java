package com.jetbrains.python.testing.pytest;

import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

/**
 * @author yole
 */
public class PyTestRunConfiguration extends AbstractPythonRunConfiguration {
  private String myTestToRun;
  private static final String TEST_TO_RUN_FIELD = "testToRun";

  public PyTestRunConfiguration(final String name, final RunConfigurationModule module, final ConfigurationFactory factory) {
    super(name, module, factory);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new PyTestRunConfiguration(getName(), getConfigurationModule(), getFactory());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PyTestConfigurationEditor(this);
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new PyTestCommandLineState(this, env);
  }

  public String getTestToRun() {
    return myTestToRun;
  }

  public void setTestToRun(String testToRun) {
    myTestToRun = testToRun;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myTestToRun = JDOMExternalizerUtil.readField(element, TEST_TO_RUN_FIELD);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, TEST_TO_RUN_FIELD, myTestToRun);
  }
}
