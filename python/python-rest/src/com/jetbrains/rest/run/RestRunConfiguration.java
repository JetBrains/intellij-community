/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.rest.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.DebugAwareConfiguration;
import org.jdom.Element;

/**
 * User : catherine
 */
public abstract class RestRunConfiguration extends AbstractPythonRunConfiguration implements DebugAwareConfiguration {
  private String myInputFile = "";
  private String myOutputFile = "";
  private String myParams = "";
  private String myTask = "";
  private boolean openInBrowser = false;

  private static final String INPUT_FILE_FIELD = "docutils_input_file";
  private static final String OUTPUT_FILE_FIELD = "docutils_output_file";
  private static final String PARAMS_FIELD = "docutils_params";
  private static final String TASK = "docutils_task";
  private static final String OPEN_IN_BROWSER = "docutils_open_in_browser";

  public RestRunConfiguration(Project project,
                              final ConfigurationFactory factory) {
    super(project, factory);
  }

  public String getInputFile() {
    return myInputFile;
  }

  public void setInputFile(String inputFile) {
    myInputFile = inputFile;
  }

  public String getOutputFile() {
    return myOutputFile;
  }

  public void setOutputFile(String file) {
    myOutputFile = file;
  }

  public void setParams(String params) {
    myParams = params;
  }

  public String getParams() {
    return myParams;
  }

  public String getTask() {
    return myTask;
  }

  public void setTask(Object task) {
    if (task != null) myTask = task.toString();
    else myTask = "rst2html";
  }

  public void setOpenInBrowser(boolean open) {
    openInBrowser = open;
  }

  public boolean openInBrowser() {
    return openInBrowser;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myInputFile = JDOMExternalizerUtil.readField(element, INPUT_FILE_FIELD);
    myOutputFile = JDOMExternalizerUtil.readField(element, OUTPUT_FILE_FIELD);
    myParams = JDOMExternalizerUtil.readField(element, PARAMS_FIELD);
    myTask = JDOMExternalizerUtil.readField(element, TASK);
    openInBrowser = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, OPEN_IN_BROWSER));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, INPUT_FILE_FIELD, myInputFile);
    JDOMExternalizerUtil.writeField(element, OUTPUT_FILE_FIELD, myOutputFile);
    JDOMExternalizerUtil.writeField(element, PARAMS_FIELD, myParams);
    JDOMExternalizerUtil.writeField(element, TASK, myTask);
    JDOMExternalizerUtil.writeField(element, OPEN_IN_BROWSER, String.valueOf(openInBrowser));
  }

  @Override
  public boolean canRunWithCoverage() {
    return false;
  }

  @Override
  public final boolean canRunUnderDebug() {
    return false; // Rest configuration can't be run under debug
  }
}
