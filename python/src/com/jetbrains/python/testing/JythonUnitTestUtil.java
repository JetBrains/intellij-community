package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.util.SystemProperties;

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

    File jythonJar = new File(PathManager.getHomePath(), "python/lib/jython.jar");
    parameters.getClassPath().add(jythonJar.getPath());

    parameters.getProgramParametersList().add("-Dpython.path=" + pythonPath + File.pathSeparator + workDir);
    parameters.getProgramParametersList().addAll(args);
    parameters.setWorkingDirectory(workDir);

    final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(sdkType.getVMExecutablePath(ideaJdk), parameters, false);
    final CapturingProcessHandler processHandler = new CapturingProcessHandler(commandLine.createProcess());
    return processHandler.runProcess();
  }
}
