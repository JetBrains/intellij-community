package org.jetbrains.plugins.ipnb.run;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSdkCredentialsHolder;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.DebugAwareConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbRunConfiguration extends AbstractPythonRunConfiguration<IpnbRunConfiguration> implements DebugAwareConfiguration {

  @NonNls private static final String ATTR_ADDITIONAL_OPTIONS = "additionalOptions";
  @NonNls private static final String ATTR_HOST = "ipnbHost";
  @NonNls private static final String ATTR_PORT = "ipnbPort";

  private String myAdditionalOptions = "";
  private String myHost = null;
  private String myPort = null;
  private String myToken;

  public IpnbRunConfiguration(Project project, ConfigurationFactory factory) {
    super(project, factory);
    setUnbufferedEnv();
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getConfigurationModule().getModule();
    if (module == null) {
      throw new ExecutionException("Please select Python module");
    }
    return new IpnbCommandLineState(this, env);
  }

  protected SettingsEditor<IpnbRunConfiguration> createConfigurationEditor() {
    return new IpnbConfigurationEditor(this);
  }

  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    myAdditionalOptions = JDOMExternalizerUtil.readField(element, ATTR_ADDITIONAL_OPTIONS);
    final String host = JDOMExternalizerUtil.readField(element, ATTR_HOST);
    myHost = host != null && host.length() > 0 ? host : null;
    final String port = JDOMExternalizerUtil.readField(element, ATTR_PORT);
    myPort = port != null && port.length() > 0 ? port : null;
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeField(element, ATTR_ADDITIONAL_OPTIONS, myAdditionalOptions);
    JDOMExternalizerUtil.writeField(element, ATTR_HOST, myHost);
    JDOMExternalizerUtil.writeField(element, ATTR_PORT, myPort);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final Module module = getConfigurationModule().getModule();
    if (module == null) {
      throw new RuntimeConfigurationError("Please select Python module");
    }
    if (StringUtil.isEmptyOrSpaces(getHost())) {
      throw new RuntimeConfigurationError("Please select valid host");
    }
    if (StringUtil.isEmptyOrSpaces(getPort())) {
      throw new RuntimeConfigurationError("Please select valid port");
    }
    final Sdk sdk = getSdk();
    if (sdk == null) return;
    if (RemoteSdkCredentialsHolder.isRemoteSdk(sdk.getHomePath())) {
      throw new RuntimeConfigurationError("Please select local python interpreter");
    }
    final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
    final PyPackage ipythonPackage = packages != null ? PyPackageUtil.findPackage(packages, "ipython") : null;
    final PyPackage jupyterPackage = packages != null ? PyPackageUtil.findPackage(packages, "jupyter") : null;
    if (ipythonPackage == null && jupyterPackage == null) {
      throw new RuntimeConfigurationError("Install Jupyter Notebook to the interpreter of the current project.",
                                          () -> ProgressManager.getInstance().run(new Task.Backgroundable(getProject(),
                                                                                                          "Installing Jupyter", false) {
                                            @Override
                                            public void run(@NotNull ProgressIndicator indicator) {
                                              try {
                                                final String version = sdk.getVersionString();
                                                if (version != null) {
                                                  final LanguageLevel level = LanguageLevel.fromPythonVersion(version);
                                                  if (level.isAtLeast(LanguageLevel.PYTHON33)) {
                                                    PyPackageManager.getInstance(sdk).install("jupyter");
                                                  }
                                                  else {
                                                    PyPackageManager.getInstance(sdk).install(Lists.newArrayList(
                                                      PyRequirement.fromLine("ipython==5"), PyRequirement.fromLine("jupyter")),
                                                                                              Lists.newArrayList());
                                                  }
                                                }
                                              }
                                              catch (ExecutionException ignored) { }
                                            }
                                          }));
    }
  }

  public String getAdditionalOptions() {
    return myAdditionalOptions;
  }

  public void setAdditionalOptions(String additionalOptions) {
    myAdditionalOptions = additionalOptions;
  }

  public String getUrl() {
    return "http://" + myHost + ":" + myPort;
  }

  public String getHost() {
    return myHost;
  }

  public String getPort() {
    return myPort;
  }

  public void setHost(@Nullable final String host) {
    myHost = host;
  }

  public void setPort(@Nullable final String port) {
    myPort = port;
  }

  public void setToken(String token) {
    myToken = token;
  }

  public String getToken() {
    return myToken;
  }

  @Override
  public boolean canRunUnderDebug() {
    return false;
  }
}
