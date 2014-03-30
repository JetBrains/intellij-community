/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.pytest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
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

  protected String myTitle = "py.test";
  protected String myPluralTitle = "py.tests";


  private static final String TEST_TO_RUN_FIELD = "testToRun";
  private static final String KEYWORDS_FIELD = "keywords";
  private static final String PARAMS_FIELD = "params";

  public PyTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory);
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
    PathMacroManager.getInstance(getProject()).expandPaths(element);
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
    PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (StringUtil.isEmptyOrSpaces(myTestToRun)) {
      throw new RuntimeConfigurationError("Please specify target folder or script");
    }
    Sdk sdkPath = PythonSdkType.findSdkByPath(getInterpreterPath());
    if (sdkPath != null && !VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdkPath))
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "py.test"));
  }

  @Override
  public String suggestedName() {
    return "py.test in " + getName();
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }
}
