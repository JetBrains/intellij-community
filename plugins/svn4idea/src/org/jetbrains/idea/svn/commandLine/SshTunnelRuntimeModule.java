// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnConfiguration.SshConnectionType;
import org.jetbrains.idea.svn.SvnConfigurationState;

import java.util.List;

import static com.intellij.execution.CommandLineUtil.toCommandLine;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class SshTunnelRuntimeModule extends BaseCommandRuntimeModule {

  public static final String DEFAULT_SSH_TUNNEL_VALUE = "$SVN_SSH ssh -q";

  public SshTunnelRuntimeModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) {
    if (!CommandRuntime.isLocal(command) && !SshConnectionType.SUBVERSION_CONFIG.equals(getState().sshConnectionType)) {
      command.put("--config-option", "config:tunnels:ssh=" + buildTunnelValue());
    }
  }

  @NotNull
  private SvnConfiguration getConfiguration() {
    return myRuntime.getVcs().getSvnConfiguration();
  }

  @NotNull
  private SvnConfigurationState getState() {
    return getConfiguration().getState();
  }

  @NotNull
  private String buildTunnelValue() {
    String sshPath = getState().sshExecutablePath;
    sshPath = !isEmpty(sshPath) ? sshPath : getExecutablePath(getConfiguration().getSshTunnelSetting());

    List<String> parameters = toCommandLine(sshPath, buildTunnelCommandLine(sshPath).getParametersList().getParameters());
    parameters.set(0, toSystemIndependentName(sshPath));

    return join(parameters, " ");
  }

  @NotNull
  private GeneralCommandLine buildTunnelCommandLine(@NotNull String sshPath) {
    GeneralCommandLine result = new GeneralCommandLine(sshPath);
    boolean isPuttyLinkClient = endsWithIgnoreCase(getNameWithoutExtension(sshPath), "plink");
    SvnConfigurationState state = getState();

    // quiet mode
    if (!isPuttyLinkClient) {
      result.addParameter("-q");
    }

    result.addParameters(isPuttyLinkClient ? "-P" : "-p", String.valueOf(state.sshPort));

    if (!isEmpty(state.sshUserName)) {
      result.addParameters("-l", state.sshUserName);
    }

    if (SshConnectionType.PRIVATE_KEY.equals(state.sshConnectionType) && !isEmpty(state.sshPrivateKeyPath)) {
      result.addParameters("-i", toSystemIndependentName(state.sshPrivateKeyPath));
    }

    return result;
  }

  @NotNull
  public static String getSshTunnelValue(@Nullable String tunnelSetting) {
    tunnelSetting = !isEmpty(tunnelSetting) ? tunnelSetting : DEFAULT_SSH_TUNNEL_VALUE;
    String svnSshVariableName = getSvnSshVariableName(tunnelSetting);
    String svnSshVariableValue = EnvironmentUtil.getValue(svnSshVariableName);

    return !isEmpty(svnSshVariableValue)
           ? svnSshVariableValue
           : !isEmpty(svnSshVariableName) ? tunnelSetting.substring(1 + svnSshVariableName.length()) : tunnelSetting;
  }

  @NotNull
  public static String getSvnSshVariableName(@Nullable String tunnel) {
    return tunnel != null && tunnel.startsWith("$") ? notNull(substringBefore(tunnel, " "), tunnel).substring(1) : "";
  }

  @NotNull
  public static String getExecutablePath(@Nullable String tunnelSetting) {
    // TODO: Add additional platform specific checks
    return notNullize(getFirstItem(ParametersListUtil.parse(getSshTunnelValue(tunnelSetting)))).trim();
  }
}
