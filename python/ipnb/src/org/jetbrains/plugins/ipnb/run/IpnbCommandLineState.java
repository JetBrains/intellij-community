package org.jetbrains.plugins.ipnb.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class IpnbCommandLineState extends PythonCommandLineState {
  private final IpnbRunConfiguration myConfiguration;

  public IpnbCommandLineState(IpnbRunConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
    myConfiguration = configuration;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    final ParametersList parametersList = commandLine.getParametersList();
    final ParamsGroup exeOptions = parametersList.getParamsGroup(GROUP_EXE_OPTIONS);
    assert exeOptions != null;
    exeOptions.addParametersString(myConfiguration.getInterpreterOptions());

    final ParamsGroup parameters = parametersList.getParamsGroup(GROUP_SCRIPT);
    assert parameters != null;

    final String home = myConfiguration.getSdkHome();
    if (home == null) return;

    String ipython = findJupyterRunner(home);
    if (ipython == null) {
      ipython = PythonHelper.LOAD_ENTRY_POINT.asParamString();
      parameters.addParameter(ipython);
      parameters.addParameter("notebook");
    }
    else {
      parameters.addParameter(ipython);
    }
    parameters.addParameter("--no-browser");

    if (myConfiguration.getHost() != null) {
      parameters.addParameter("--ip");
      parameters.addParameter(myConfiguration.getHost());
    }
    if (myConfiguration.getPort() != null) {
      parameters.addParameter("--port");
      parameters.addParameter(myConfiguration.getPort());
    }
    if (myConfiguration.getPort() != null) {
      parameters.addParameter("--port-retries=0");
    }
    final String additionalOptions = myConfiguration.getAdditionalOptions();
    if (!StringUtil.isEmptyOrSpaces(additionalOptions)) {
      parameters.addParameters(StringUtil.split(additionalOptions, " "));
    }

    final String workingDirectory = myConfiguration.getWorkingDirectory();
    commandLine.setWorkDirectory(!StringUtil.isEmptyOrSpaces(workingDirectory) ?
                                 workingDirectory : myConfiguration.getProject().getBasePath());
  }

  @Override
  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
    super.customizeEnvironmentVars(envs, passParentEnvs);
    final String home = myConfiguration.getSdkHome();
    if (home != null) {
      String ipython = findJupyterRunner(home);
      if (ipython == null) {
        envs.put("PYCHARM_EP_DIST", "ipython");
        envs.put("PYCHARM_EP_NAME", "ipython");
      }
    }
  }

  @Nullable
  public static Pair<String, String> getHostPortFromUrl(@NotNull final String url) {
    try {
      final URI uri = new URI(url);
      final int port = uri.getPort();
      return Pair.create(uri.getHost(), port == -1 ? null : String.valueOf(port));
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  @Nullable
  private static String findJupyterRunner(@NotNull final String homePath) {
    for (String name : Lists.newArrayList("jupyter-notebook", "jupyter")) {
      String runnerPath = PythonSdkType.getExecutablePath(homePath, name);
      if (runnerPath != null) {
        return runnerPath;
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess(PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = super.startProcess(processStarter, patchers);
    addTokenListener(processHandler);
    return processHandler;
  }

  public void addTokenListener(ProcessHandler processHandler) {
    final Ref<Boolean> serverStarted = new Ref<>(false);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        @NonNls final String text = event.getText();
        if (text.toLowerCase().contains("active kernels")) {
          serverStarted.set(true);
        }
        final String token = "?token=";
        if (text.toLowerCase().contains(token) && StringUtil.isEmpty(myConfiguration.getToken())) {
          myConfiguration.setToken(text.substring(text.indexOf(token) + token.length()).trim());
        }
      }
    });
  }
}
