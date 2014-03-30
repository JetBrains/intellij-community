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
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TODO: Use {@link com.jetbrains.python.run.PythonRunner} instead of this class? At already supports rerun and other things
 * Base class for tasks which are run from PyCharm with results displayed in a toolwindow (manage.py, setup.py, Sphinx etc).
 *
 * @author yole
 */
public class PythonTask {
  /**
   * Mils we wait to process to be stopped when "rerun" called
   */
  private static final long TIME_TO_WAIT_PROCESS_STOP = 2000L;
  protected final Module myModule;
  private final Sdk mySdk;
  private String myWorkingDirectory;
  private String myRunnerScript;
  private List<String> myParameters = new ArrayList<String>();
  private final String myRunTabTitle;
  private String myHelpId;
  private Runnable myAfterCompletion;

  public PythonTask(Module module, String runTabTitle) throws ExecutionException {
    this(module, runTabTitle, PythonSdkType.findPythonSdk(module));
  }

  public PythonTask(final Module module, final String runTabTitle, @Nullable final Sdk sdk) throws ExecutionException {
    myModule = module;
    myRunTabTitle = runTabTitle;
    mySdk = sdk;
    if (mySdk == null) {
      throw new ExecutionException("Cannot find Python interpreter for selected module");
    }
  }

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public void setRunnerScript(String script) {
    myRunnerScript = script;
  }

  public void setParameters(List<String> parameters) {
    myParameters = parameters;
  }

  public void setHelpId(String helpId) {
    myHelpId = helpId;
  }

  public void setAfterCompletion(Runnable afterCompletion) {
    myAfterCompletion = afterCompletion;
  }

  /**
   * @param env environment variables to be passed to process or null if nothing should be passed
   */
  public ProcessHandler createProcess(@Nullable final Map<String, String> env) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLine();
    if (env != null) {
       commandLine.getEnvironment().putAll(env);
    }

    ProcessHandler handler;
    if (PySdkUtil.isRemote(mySdk)) {
      assert mySdk != null;
      handler = new PyRemoteProcessStarter().startRemoteProcess(mySdk, commandLine, myModule.getProject(), null);
    }
    else {
      EncodingEnvironmentUtil.fixDefaultEncodingIfMac(commandLine, myModule.getProject());
      handler = PythonProcessRunner.createProcessHandlingCtrlC(commandLine);

      ProcessTerminatedListener.attach(handler);
    }
    return handler;
  }


  public GeneralCommandLine createCommandLine() {
    GeneralCommandLine cmd = new GeneralCommandLine();

    if (myWorkingDirectory != null) {
      cmd.setWorkDirectory(myWorkingDirectory);
    }

    String homePath = mySdk.getHomePath();
    if (homePath != null) {
      homePath = FileUtil.toSystemDependentName(homePath);
    }

    PythonCommandLineState.createStandardGroupsIn(cmd);
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;

    cmd.setPassParentEnvironment(true);
    Map<String, String> env = cmd.getEnvironment();
    if (!SystemInfo.isWindows && !PySdkUtil.isRemote(mySdk)) {
      cmd.setExePath("bash");
      ParamsGroup bashParams = cmd.getParametersList().addParamsGroupAt(0, "Bash");
      bashParams.addParameter("-cl");

      NotNullFunction<String, String> escaperFunction = StringUtil.escaper(false, "|>$\"'& ");
      StringBuilder paramString = new StringBuilder(escaperFunction.fun(homePath) + " " + escaperFunction.fun(myRunnerScript));

      for (String p : myParameters) {
        paramString.append(" ").append(p);
      }
      bashParams.addParameter(paramString.toString());
    }
    else {
      cmd.setExePath(homePath);
      scriptParams.addParameter(myRunnerScript);
      scriptParams.addParameters(myParameters);
    }

    PythonEnvUtil.setPythonUnbuffered(env);

    List<String> pythonPath = setupPythonPath();
    PythonCommandLineState.initPythonPath(cmd, true, pythonPath, homePath);

    BuildoutFacet facet = BuildoutFacet.getInstance(myModule);
    if (facet != null) {
      facet.patchCommandLineForBuildout(cmd);
    }

    return cmd;
  }

  protected List<String> setupPythonPath() {
    return setupPythonPath(true, true);
  }

  protected List<String> setupPythonPath(final boolean addContent, final boolean addSource) {
    final List<String> pythonPath = Lists.newArrayList(PythonCommandLineState.getAddedPaths(mySdk));
    pythonPath.addAll(PythonCommandLineState.collectPythonPath(myModule, addContent, addSource));
    return pythonPath;
  }

  /**
   * @param env environment variables to be passed to process or null if nothing should be passed
   */
  public void run(@Nullable final Map<String, String> env) throws ExecutionException {
    final ProcessHandler process = createProcess(env);
    final Project project = myModule.getProject();
    new RunContentExecutor(project, process)
      .withFilter(new PythonTracebackFilter(project))
      .withTitle(myRunTabTitle)
      .withRerun(new Runnable() {
        @Override
        public void run() {
          try {
            process.destroyProcess(); // Stop process before rerunning it
            if (process.waitFor(TIME_TO_WAIT_PROCESS_STOP)) {
              PythonTask.this.run(env);
            }else {
              Messages.showErrorDialog(PyBundle.message("unable.to.stop"), myRunTabTitle);
            }
          }
          catch (ExecutionException e) {
            Messages.showErrorDialog(e.getMessage(), myRunTabTitle);
          }
        }
      })
      .withStop(new Runnable() {
                  @Override
                  public void run() {
                    process.destroyProcess();
                  }
                }, new Computable<Boolean>() {

                  @Override
                  public Boolean compute() {
                    return !process.isProcessTerminated();
                  }
                }
      )
      .withAfterCompletion(myAfterCompletion)
      .withHelpId(myHelpId)
      .run();
  }
}
