package com.jetbrains.python.testing.pytest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTestRunConfiguration extends AbstractPythonTestRunConfiguration implements PyTestRunConfigurationParams {
  private String myTestToRun = "";
  private String myKeywords = "";
  private String myParams = "";
  private boolean useParam = false;
  private boolean useKeyword = false;

  private static final String TEST_TO_RUN_FIELD = "testToRun";
  private static final String KEYWORDS_FIELD = "keywords";
  private static final String PARAMS_FIELD = "params";

  public PyTestRunConfiguration(final String name, final RunConfigurationModule module, final ConfigurationFactory factory) {
    super(module, factory, name);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new PyTestRunConfiguration(getName(), getConfigurationModule(), getFactory());
  }

  protected SettingsEditor<? extends RunConfiguration> createConfigurationEditor() {
    return new PyTestConfigurationEditor(getProject(), this);
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
    if (useKeyword)
      return myKeywords;
    return "";
  }

  public void setKeywords(String keywords) {
    myKeywords = keywords;
  }

  public void setParams(String params) {
    myParams = params;
  }

  public String getParams() {
    if (useParam)
      return myParams;
    return "";
  }

  public boolean useParam() {
    return useParam;
  }

  public void useParam(boolean useParam) {
    this.useParam = useParam;
  }

  public boolean useKeyword() {
    return useKeyword;
  }

  public void useKeyword(boolean useKeyword) {
    this.useKeyword = useKeyword;
  }


  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myTestToRun = JDOMExternalizerUtil.readField(element, TEST_TO_RUN_FIELD);
    myKeywords = JDOMExternalizerUtil.readField(element, KEYWORDS_FIELD);
    myParams = JDOMExternalizerUtil.readField(element, PARAMS_FIELD);
    useParam = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_PARAM"));
    useKeyword = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_KEYWORD"));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, TEST_TO_RUN_FIELD, myTestToRun);
    JDOMExternalizerUtil.writeField(element, KEYWORDS_FIELD, myKeywords);
    JDOMExternalizerUtil.writeField(element, PARAMS_FIELD, myParams);
    JDOMExternalizerUtil.writeField(element, "USE_PARAM", String.valueOf(useParam));
    JDOMExternalizerUtil.writeField(element, "USE_KEYWORD", String.valueOf(useKeyword));
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (StringUtil.isEmptyOrSpaces(myTestToRun)) {
      throw new RuntimeConfigurationError("Please specify target folder or script");
    }
    if (!VFSTestFrameworkListener.getInstance().isPyTestInstalled(getSdkHome()))
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "py.test"));
  }

  @Override
  public String suggestedName() {
    return "py.test in " + getName();
  }

  @Override
  protected String getTitle() {
    return "py.tests";
  }
}
