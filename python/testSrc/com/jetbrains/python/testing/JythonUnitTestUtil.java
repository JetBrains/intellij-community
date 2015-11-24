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
package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.PythonHelpersLocator;

import java.io.File;

/**
 * @author traff
 */
public class JythonUnitTestUtil {
  private JythonUnitTestUtil() {
  }

  public static ProcessOutput runJython(String workDir, String pythonPath, String... args) throws ExecutionException {
    final SimpleJavaSdkType sdkType = new SimpleJavaSdkType();
    final Sdk ideaJdk = sdkType.createJdk("tmp", SystemProperties.getJavaHome());
    SimpleJavaParameters parameters = new SimpleJavaParameters();
    parameters.setJdk(ideaJdk);
    parameters.setMainClass("org.python.util.jython");

    File jythonJar = new File(PythonHelpersLocator.getPythonCommunityPath(), "lib/jython.jar");
    parameters.getClassPath().add(jythonJar.getPath());

    parameters.getProgramParametersList().add("-Dpython.path=" + pythonPath + File.pathSeparator + workDir);
    parameters.getProgramParametersList().addAll(args);
    parameters.setWorkingDirectory(workDir);

    final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(sdkType.getVMExecutablePath(ideaJdk), parameters, false);
    final CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine);
    return processHandler.runProcess();
  }
}
