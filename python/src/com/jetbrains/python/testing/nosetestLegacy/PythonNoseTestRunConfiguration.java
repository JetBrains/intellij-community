/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.testing.nosetestLegacy;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.AbstractPythonLegacyTestRunConfiguration;
import com.jetbrains.python.testing.VFSTestFrameworkListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class PythonNoseTestRunConfiguration extends AbstractPythonLegacyTestRunConfiguration<PythonNoseTestRunConfiguration>
                                          implements PythonNoseTestRunConfigurationParams {
  private String myParams = ""; // parameters for nosetests
  protected String myTitle = "Nosetest";
  protected String myPluralTitle = "Nosetests";
  private boolean useParam = false;

  public PythonNoseTestRunConfiguration(Project project,
                                        ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  protected SettingsEditor<PythonNoseTestRunConfiguration> createConfigurationEditor() {
    return new PythonNoseTestRunConfigurationEditor(getProject(), this);
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);
    myParams = JDOMExternalizerUtil.readField(element, "PARAMS");
    useParam = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_PARAM"));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "PARAMS", myParams);
    JDOMExternalizerUtil.writeField(element, "USE_PARAM", String.valueOf(useParam));
  }

  @Override
  protected String getTitle() {
    return myTitle;
  }

  @Override
  protected String getPluralTitle() {
    return myPluralTitle;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    return new PythonNoseTestCommandLineState(this, env);
  }

  public static void copyParams(PythonNoseTestRunConfigurationParams source, PythonNoseTestRunConfigurationParams target) {
    AbstractPythonLegacyTestRunConfiguration.copyParams(source.getTestRunConfigurationParams(), target.getTestRunConfigurationParams());
    target.setParams(source.getParams());
    target.useParam(source.useParam());
  }

  public String getParams() {
    return myParams;
  }

  public void setParams(String pattern) {
    myParams = pattern;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final Sdk sdk = PythonSdkType.findSdkByPath(getInterpreterPath());
    if (sdk != null && !VFSTestFrameworkListener.getInstance().isTestFrameworkInstalled(sdk, PyNames.NOSE_TEST))
      throw new RuntimeConfigurationWarning(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"));
  }

  public boolean useParam() {
    return useParam;
  }

  public void useParam(boolean useParam) {
    this.useParam = useParam;
  }
}
