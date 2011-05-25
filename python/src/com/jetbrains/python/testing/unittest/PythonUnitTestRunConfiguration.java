package com.jetbrains.python.testing.unittest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestRunConfiguration extends
                                            AbstractPythonTestRunConfiguration
                                              implements PythonUnitTestRunConfigurationParams {
  private String myPattern = ""; // pattern for modules in folder to match against
  private boolean myIsPureUnittest = true;
  protected String myTitle = "Unittests";

  protected PythonUnitTestRunConfiguration(RunConfigurationModule module, ConfigurationFactory configurationFactory, String name) {
    super(module, configurationFactory, name);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new PythonUnitTestRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonUnitTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonUnitTestCommandLineState(this, env);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myPattern = JDOMExternalizerUtil.readField(element, "PATTERN");
    myIsPureUnittest = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "PUREUNITTEST"));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "PATTERN", myPattern);
    JDOMExternalizerUtil.writeField(element, "PUREUNITTEST", String.valueOf(myIsPureUnittest));
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  public static void copyParams(PythonUnitTestRunConfigurationParams source, PythonUnitTestRunConfigurationParams target) {
    copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
    target.setPattern(source.getPattern());
    target.setPureUnittest(source.isPureUnittest());
  }

  public String getPattern() {
    return myPattern;
  }

  public void setPattern(String pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean isPureUnittest() {
    return myIsPureUnittest;
  }

  public void setPureUnittest(boolean isPureUnittest) {
    myIsPureUnittest = isPureUnittest;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (myIsPureUnittest && !myScriptName.startsWith("test")) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.incorrect.script"));
    }
  }
}
