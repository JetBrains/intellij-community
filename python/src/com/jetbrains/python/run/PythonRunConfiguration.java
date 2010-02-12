package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonRunConfiguration extends AbstractPythonRunConfiguration 
  implements AbstractPythonRunConfigurationParams, PythonRunConfigurationParams, CommandLinePatcher
{
  private String myScriptName;
  private String myScriptParameters;

  protected PythonRunConfiguration(RunConfigurationModule module, ConfigurationFactory configurationFactory, String name) {
    super(name, module, configurationFactory);
    addDefaultEnvs();
  }

  private void addDefaultEnvs() {
    Map<String, String> envs = getEnvs();
    // unbuffered I/O is easier for IDE to handle
    envs.put("PYTHONUNBUFFERED", "1");
  }

  protected ModuleBasedConfiguration createInstance() {
    return new PythonRunConfiguration(getConfigurationModule(), getFactory(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new PythonRunConfigurationEditor(this);
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
    List<Filter> filters = new ArrayList<Filter>();
    filters.add(new PythonTracebackFilter(getProject(), getWorkingDirectory()));

    return new PythonScriptCommandLineState(this, env, filters);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(myScriptName)) {
      throw new RuntimeConfigurationException(PyBundle.message("runcfg.unittest.no_script_name"));
    }
  }

  public boolean isGeneratedName() {
    return Comparing.equal(getName(), suggestedName());
  }

  public String suggestedName() {
    String name = new File(getScriptName()).getName();
    if (name.endsWith(".py")) {
      return name.substring(0, name.length() - 3);
    }
    return name;
  }

  public String getScriptName() {
    return myScriptName;
  }

  public void setScriptName(String scriptName) {
    myScriptName = scriptName;
  }

  public String getScriptParameters() {
    return myScriptParameters;
  }

  public void setScriptParameters(String scriptParameters) {
    myScriptParameters = scriptParameters;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myScriptName = JDOMExternalizerUtil.readField(element, "SCRIPT_NAME");
    myScriptParameters = JDOMExternalizerUtil.readField(element, "PARAMETERS");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, "SCRIPT_NAME", myScriptName);
    JDOMExternalizerUtil.writeField(element, "PARAMETERS", myScriptParameters);
  }

  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  public static void copyParams(PythonRunConfigurationParams source, PythonRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setScriptParameters(source.getScriptParameters());
  }

  /**
   * Some setups (e.g. virtualenv) provide a script that alters environment variables before running a python interpreter or other tools.
   * Such settings are not directly stored but applied right before running using this method.
   * @param commandLine what to patch
   */
  public void patchCommandLine(GeneralCommandLine commandLine) {
    // prepend virtualenv bin if it's not already on PATH
    final String sdk_home = getSdkHome();
    if (sdk_home != null) {
      File virtualenv_root = PythonSdkType.getVirtualEnvRoot(sdk_home);
      if (virtualenv_root != null) {
        String virtualenv_bin = new File(virtualenv_root, "bin").getPath();
        String path_value;
        if (isPassParentEnvs()) {
          // append to PATH
          path_value = System.getenv("PATH");
          if (path_value != null) path_value = virtualenv_bin + File.pathSeparator + path_value;
          else path_value = virtualenv_bin;
        }
        else path_value = virtualenv_bin;
        Map<String, String> new_env; // we need a copy lest we change config's map.
        Map<String, String> cmd_env = commandLine.getEnvParams();
        if (cmd_env != null) new_env = new HashMap<String, String>(cmd_env);
        else new_env = new HashMap<String, String>();
        String existing_path = new_env.get("PATH");
        if (existing_path == null || existing_path.indexOf(virtualenv_bin) < 0) {
          new_env.put("PATH", path_value);
          commandLine.setEnvParams(new_env);
        }
      }
    }
  }
}
