package com.intellij.execution.util;

import com.intellij.execution.CantRunException;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit2.configuration.RunConfigurationModule;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;

/**
 * User: lex
 * Date: Nov 26, 2003
 * Time: 10:38:01 PM
 */
public class JavaParametersUtil {
  public static void configureConfiguration(final JavaParameters parameters, final RunJavaConfiguration configuration) {
    parameters.getProgramParametersList().addParametersString(configuration.getProperty(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY));
    parameters.getVMParametersList().addParametersString(configuration.getProperty(RunJavaConfiguration.VM_PARAMETERS_PROPERTY));
    String workingDirectory = configuration.getProperty(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY);
    if (workingDirectory == null || workingDirectory.trim().length() == 0) {
      final VirtualFile projectFile = configuration.getProject().getProjectFile();
      if (projectFile != null) {
        workingDirectory = PathUtil.getLocalPath(projectFile.getParent());
      }
    }
    parameters.setWorkingDirectory(workingDirectory);
  }

  public static void configureModule(final RunConfigurationModule runConfigurationModule, final JavaParameters parameters, final int classPathType) throws CantRunException {
    if(runConfigurationModule.getModule() == null) {
      throw CantRunException.noModuleConfigured(runConfigurationModule.getModuleName());
    }
    parameters.configureByModule(runConfigurationModule.getModule(), classPathType);
  }
}
