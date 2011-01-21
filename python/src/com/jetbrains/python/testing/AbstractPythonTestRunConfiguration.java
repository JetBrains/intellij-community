package com.jetbrains.python.testing;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import org.jdom.Element;

/**
 * User: catherine
 */
public abstract class AbstractPythonTestRunConfiguration extends AbstractPythonRunConfiguration
                            implements AbstractPythonRunConfigurationParams,
                                       AbstractPythonTestRunConfigurationParams {
  protected String myClassName = "";
  protected String myScriptName = "";
  protected String myMethodName = "";
  protected String myFolderName = "";
  protected TestType myTestType = TestType.TEST_SCRIPT;

  protected AbstractPythonTestRunConfiguration(RunConfigurationModule module, ConfigurationFactory configurationFactory, String name) {
    super(name, module, configurationFactory);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
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
  }

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

  public enum TestType {
    TEST_FOLDER,
    TEST_SCRIPT,
    TEST_CLASS,
    TEST_METHOD,
    TEST_FUNCTION,}

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myFolderName) && myTestType == TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyBundle.message("runcfg.unittest.no_folder_name"));
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

  public boolean compareSettings(AbstractPythonTestRunConfiguration cfg) {
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

  public static void copyParams(AbstractPythonTestRunConfigurationParams source, AbstractPythonTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
  }

  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return this;
  }

  @Override
  public String suggestedName() {
    switch (myTestType) {
      case TEST_CLASS:
        return getTitle() + " in " + myClassName;
      case TEST_METHOD:
        return getTitle() + " in " + myClassName + "." + myMethodName;
      case TEST_SCRIPT:
        return getTitle() + " in " + myScriptName;
      case TEST_FOLDER:
        return getTitle() + " in " + FileUtil.toSystemDependentName(myFolderName);
      case TEST_FUNCTION:
        return getTitle() + " in " + myMethodName;
      default:
        throw new IllegalStateException("Unknown test type: " + myTestType);
    }
  }

  protected abstract String getTitle();
}
