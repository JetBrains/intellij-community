// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathMapper;
import com.jetbrains.python.console.PyConsoleOptions;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class PythonConsoleScripts {
  /**
   * Composes lines for execution in Python Console to run Python script specified in the given {@code config}.
   * <p>
   * Uses {@code runfile()} method defined in {@code _pydev_bundle/pydev_umd.py}.
   *
   * @param config
   * @return the program
   */
  public static @NotNull String buildScriptWithConsoleRun(@NotNull PythonRunConfiguration config) {
    StringBuilder sb = new StringBuilder();
    final Map<String, String> configEnvs = config.getEnvs();
    configEnvs.remove(PythonEnvUtil.PYTHONUNBUFFERED);
    if (configEnvs.size() > 0) {
      sb.append("import os\n");
      for (Map.Entry<String, String> entry : configEnvs.entrySet()) {
        sb.append("os.environ['").append(escape(entry.getKey())).append("'] = '").append(escape(entry.getValue())).append("'\n");
      }
    }

    final Project project = config.getProject();
    final Sdk sdk = config.getSdk();
    final PathMapper pathMapper =
      PydevConsoleRunner.getPathMapper(project, sdk, PyConsoleOptions.getInstance(project).getPythonConsoleSettings());

    String scriptPath = config.getScriptName();
    String workingDir = config.getWorkingDirectory();
    if (PythonSdkUtil.isRemote(sdk) && pathMapper != null) {
      scriptPath = pathMapper.convertToRemote(scriptPath);
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    sb.append("runfile('").append(escape(scriptPath)).append("'");

    final List<String> scriptParameters = ProgramParametersConfigurator.expandMacrosAndParseParameters(config.getScriptParameters());
    if (scriptParameters.size() != 0) {
      sb.append(", args=[");
      for (int i = 0; i < scriptParameters.size(); i++) {
        if (i != 0) {
          sb.append(", ");
        }
        sb.append("'").append(escape(scriptParameters.get(i))).append("'");
      }
      sb.append("]");
    }

    if (!workingDir.isEmpty()) {
      sb.append(", wdir='").append(escape(workingDir)).append("'");
    }

    if (config.isModuleMode()) {
      sb.append(", is_module=True");
    }

    sb.append(")");
    return sb.toString();
  }

  @Contract(pure = true)
  private static @NotNull String escape(@NotNull String s) {
    return StringUtil.escapeCharCharacters(s);
  }
}
