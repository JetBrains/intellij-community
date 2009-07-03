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
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jdom.Element;

import java.io.File;

/**
 * @author yole
 */
public class PyTestRunConfiguration extends AbstractPythonRunConfiguration {
  private String myTestToRun;
  private String myKeywords;

  private static final String TEST_TO_RUN_FIELD = "testToRun";
  private static final String KEYWORDS_FIELD = "keywords";

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

  public String getKeywords() {
    return myKeywords;
  }

  public void setKeywords(String keywords) {
    myKeywords = keywords;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myTestToRun = JDOMExternalizerUtil.readField(element, TEST_TO_RUN_FIELD);
    myKeywords = JDOMExternalizerUtil.readField(element, KEYWORDS_FIELD);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, TEST_TO_RUN_FIELD, myTestToRun);
    JDOMExternalizerUtil.writeField(element, KEYWORDS_FIELD, myKeywords);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    if (!new File(getRunnerScriptPath()).exists()) {
      throw new RuntimeConfigurationError("No py.test runner found in selected interpreter");
    }    
  }

  @Nullable
  public String getRunnerScriptPath() {
    String sdkHome = getSdkHome();
    String runnerExt = SystemInfo.isWindows ? ".exe" : "";
    File runner = new File(sdkHome, "scripts/py.test" + runnerExt);
    return runner.getPath();
  }
}
