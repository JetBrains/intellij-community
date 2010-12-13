package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 */
public class PythonNoseTestRunConfiguration extends AbstractPythonRunConfiguration
    implements AbstractPythonRunConfigurationParams,
                                    PythonNoseTestRunConfigurationParams {
  private String myClassName = "";
  private String myScriptName = "";
  private String myMethodName = "";
  private String myFolderName = "";
  private TestType myTestType = TestType.TEST_SCRIPT;
  private String myParams = ""; // parameters for nosetests

  protected PythonNoseTestRunConfiguration(RunConfigurationModule module,
                                     ConfigurationFactory configurationFactory, String name) {
    super(name, module, configurationFactory);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new PythonNoseTestRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  public enum TestType {
    TEST_FOLDER,
    TEST_SCRIPT,
    TEST_CLASS,
    TEST_METHOD,
    TEST_FUNCTION,}

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myParams = JDOMExternalizerUtil.readField(element, "PARAMS");
    myScriptName = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME");
    myClassName = JDOMExternalizerUtil.readField(element, "CLASS_NAME");
    myMethodName = JDOMExternalizerUtil.readField(element, "METHOD_NAME");
    myFolderName = JDOMExternalizerUtil.readField(element, "FOLDER_NAME");

    try {
      myTestType = TestType.valueOf(JDOMExternalizerUtil.readField(element, "TEST_TYPE"));
    }
    catch (IllegalArgumentException e) {
      myTestType = TestType.TEST_SCRIPT; // safe default
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", myScriptName);
    JDOMExternalizerUtil.writeField(element, "CLASS_NAME", myClassName);
    JDOMExternalizerUtil.writeField(element, "METHOD_NAME", myMethodName);
    JDOMExternalizerUtil.writeField(element, "FOLDER_NAME", myFolderName);
    JDOMExternalizerUtil.writeField(element, "TEST_TYPE", myTestType.toString());
    JDOMExternalizerUtil.writeField(element, "PARAMS", myParams);
  }

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonNoseTestRunConfigurationEditor(getProject(), this);
  }

  public String getParams() {
    return myParams;
  }

  public void setParams(String pattern) {
    myParams = pattern;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonNoseTestCommandLineState(this, env);
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  public String getClassName() {
    return myClassName;
  }

  public void setClassName(String className) {
    myClassName = className;
  }

  public String getFolderName() {
    return myFolderName;
  }

  public void setFolderName(String folderName) {
    myFolderName = folderName;
  }

  public String getScriptName() {
    return myScriptName;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public String getMethodName() {
    return myMethodName;
  }

  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  public TestType getTestType() {
    return myTestType;
  }

  public void setTestType(TestType testType) {
    myTestType = testType;
  }
  @Override
  public String suggestedName() {
    switch (myTestType) {
      case TEST_CLASS:
        return "Nosetests in " + myClassName;
      case TEST_METHOD:
        return "Nosetests " + myClassName + "." + myMethodName;
      case TEST_SCRIPT:
        return "Nosetests in " + myScriptName;
      case TEST_FOLDER:
        return "Nosetests in " + FileUtil.toSystemDependentName(myFolderName);
      case TEST_FUNCTION:
        return "Nosetests " + myMethodName;
      default:
        throw new IllegalStateException("Unknown test type: " + myTestType);
    }
  }

  public static void copyParams(PythonNoseTestRunConfigurationParams source, PythonNoseTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
    target.setParams(source.getParams());
  }

  public boolean compareSettings(PythonNoseTestRunConfiguration cfg) {
    if (cfg == null) return false;

    if (getTestType() != cfg.getTestType()) return false;

    switch (getTestType()) {
      case TEST_FOLDER:
        return getFolderName().equals(cfg.getFolderName());
      case TEST_SCRIPT:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory());
      case TEST_CLASS:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getClassName().equals(cfg.getClassName());
      case TEST_METHOD:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getClassName().equals(cfg.getClassName()) &&
               getMethodName().equals(cfg.getMethodName());
      case TEST_FUNCTION:
        return getScriptName().equals(cfg.getScriptName()) &&
               getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
               getMethodName().equals(cfg.getMethodName());
      default:
        throw new IllegalStateException("Unknown test type: " + getTestType());
    }
  }
  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myFolderName) && myTestType == TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(
        PyBundle.message("runcfg.unittest.no_folder_name"));
    }

    if (StringUtil.isEmptyOrSpaces(getScriptName()) && myTestType != TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_script_name"));
    }

    if (StringUtil.isEmptyOrSpaces(myClassName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_CLASS)) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_class_name"));
    }

    if (StringUtil.isEmptyOrSpaces(myMethodName) && (myTestType == TestType.TEST_METHOD || myTestType == TestType.TEST_FUNCTION)) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_method_name"));
    }
  }

}
