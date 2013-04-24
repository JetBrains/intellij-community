package com.jetbrains.python.sdk.flavors;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.run.PythonCommandLineState;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author yole
 */
public class JythonSdkFlavor extends PythonSdkFlavor {
  private static final String JYTHONPATH = "JYTHONPATH";
  private static final String PYTHON_PATH_PREFIX = "-Dpython.path=";

  private JythonSdkFlavor() {
  }

  public static JythonSdkFlavor INSTANCE = new JythonSdkFlavor();

  public boolean isValidSdkPath(@NotNull File file) {
    return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("jython");
  }

  @Override
  public String getVersionRegexp() {
    return "(Jython \\S+)( on .*)?";
  }

  @Override
  public String getVersionOption() {
    return "--version";
  }

  @Override
  public void initPythonPath(GeneralCommandLine cmd, Collection<String> path) {
    initPythonPath(path, cmd.getEnvironment());
    ParamsGroup paramGroup = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_EXE_OPTIONS);
    assert paramGroup != null;
    for (String param : paramGroup.getParameters()) {
      if (param.startsWith(PYTHON_PATH_PREFIX)) {
        return;
      }
    }
    paramGroup.addParameter(getPythonPathCmdLineArgument(path));
  }

  @Override
  public void initPythonPath(Collection<String> path, Map<String, String> env) {
    path = appendSystemEnvPaths(path, JYTHONPATH);
    final String jythonPath = StringUtil.join(path, File.pathSeparator);
    addToEnv(JYTHONPATH, jythonPath, env);
  }

  @NotNull
  @Override
  public String getName() {
    return "Jython";
  }

  public static String getPythonPathCmdLineArgument(Collection<String> path) {
    return PYTHON_PATH_PREFIX + StringUtil.join(appendSystemEnvPaths(path, JYTHONPATH), File.pathSeparator);
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Jython;
  }
}
