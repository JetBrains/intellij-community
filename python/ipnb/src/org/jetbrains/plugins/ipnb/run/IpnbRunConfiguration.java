package org.jetbrains.plugins.ipnb.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.DebugAwareConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    myAdditionalOptions = JDOMExternalizerUtil.readField(element, ATTR_ADDITIONAL_OPTIONS);
    final String host = JDOMExternalizerUtil.readField(element, ATTR_HOST);
    myHost = host != null && host.length() > 0 ? host : null;
    final String port = JDOMExternalizerUtil.readField(element, ATTR_PORT);
    myPort = port != null && port.length() > 0 ? port : null;
  }

  public void writeExternal(Element element) throws WriteExternalException {
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
