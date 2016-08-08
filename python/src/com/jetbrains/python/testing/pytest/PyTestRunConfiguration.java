/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.AbstractPythonTestRunConfiguration;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

  @NotNull
  @Override
  public String getWorkingDirectorySafe() {
    final String workingDirectoryFromConfig = getWorkingDirectory();
    if (StringUtil.isNotEmpty(workingDirectoryFromConfig)) {
      return workingDirectoryFromConfig;
    }
    final String testToRun = myTestToRun;
    if (testToRun != null) {
      final VirtualFile path = LocalFileSystem.getInstance().findFileByPath(testToRun);
      if (path != null) {
        if (path.isDirectory()) {
          return path.getPath();
        }
        return path.getParent().getPath();
      }
    }
    return super.getWorkingDirectorySafe();
  }

  public void setTestToRun(String testToRun) {
    myTestToRun = testToRun;
  }

  public String getKeywords() {
    if (useKeyword) {
      return myKeywords;
    }
    return "";
  }

  public void setKeywords(String keywords) {
    myKeywords = keywords;
  }

  public void setParams(String params) {
    myParams = params;
  }

  public String getParams() {
    if (useParam) {
      return myParams;
    }
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
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (StringUtil.isEmptyOrSpaces(myTestToRun)) {
      throw new RuntimeConfigurationError("Please specify target folder or script");
    }
    Sdk sdkPath = PythonSdkType.findSdkByPath(getInterpreterPath());
    if (sdkPath != null && !VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdkPath)) {
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "py.test"));
    }
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

  @Nullable
  @Override
  public final String getTestSpec(@NotNull final Location location, @NotNull final AbstractTestProxy failedTest) {
    /**
     *  PyTest supports subtests (with yielding). Such tests are reported as _test_name[index] and location does not point to actual test.
     *  We need to get rid of braces and calculate name manually, since location is incorrect.
     *  Test path starts from file.
     */
    final int indexOfBrace = failedTest.getName().indexOf('[');
    if (indexOfBrace == -1) {
      return super.getTestSpec(location, failedTest);
    }
    final List<String> testNameParts = new ArrayList<>();
    final VirtualFile file = location.getVirtualFile();
    if (file == null) {
      return null;
    }
    final String fileName = file.getName();

    testNameParts.add(failedTest.getName().substring(0, indexOfBrace));
    for (AbstractTestProxy test = failedTest.getParent(); test != null && !test.getName().equals(fileName); test = test.getParent()) {
      testNameParts.add(test.getName());
    }
    testNameParts.add(file.getCanonicalPath());
    return StringUtil.join(Lists.reverse(testNameParts), TEST_NAME_PARTS_SPLITTER);
  }
}
