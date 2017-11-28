/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnConfigurationState;

/**
 * @author Konstantin Kolosovsky.
 */
public class SshTunnelRuntimeModule extends BaseCommandRuntimeModule {

  public static final String DEFAULT_SSH_TUNNEL_VALUE = "$SVN_SSH ssh -q";

  public SshTunnelRuntimeModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) {
    if (!CommandRuntime.isLocal(command)) {
      if (!SvnConfiguration.SshConnectionType.SUBVERSION_CONFIG.equals(getState().sshConnectionType)) {
        command.put("--config-option", "config:tunnels:ssh=" + StringUtil.notNullize(buildTunnelValue()));
      }
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

  @Nullable
  private String buildTunnelValue() {
    String sshPath = getState().sshExecutablePath;
    sshPath = !StringUtil.isEmpty(sshPath) ? sshPath : getExecutablePath(getConfiguration().getSshTunnelSetting());

    return StringUtil
      .join(CommandLineUtil.toCommandLine(sshPath, buildTunnelCommandLine(sshPath).getParametersList().getParameters()), " ");
  }

  @NotNull
  private GeneralCommandLine buildTunnelCommandLine(@NotNull String sshPath) {
    GeneralCommandLine result = new GeneralCommandLine(sshPath);
    boolean isPuttyLinkClient = StringUtil.endsWithIgnoreCase(FileUtil.getNameWithoutExtension(sshPath), "plink");
    SvnConfigurationState state = getState();

    // quiet mode
    if (!isPuttyLinkClient) {
      result.addParameter("-q");
    }

    result.addParameters(isPuttyLinkClient ? "-P" : "-p", String.valueOf(state.sshPort));

    if (!StringUtil.isEmpty(state.sshUserName)) {
      result.addParameters("-l", state.sshUserName);
    }

    if (SvnConfiguration.SshConnectionType.PRIVATE_KEY.equals(state.sshConnectionType) && !StringUtil.isEmpty(state.sshPrivateKeyPath)) {
      result.addParameters("-i", FileUtil.toSystemIndependentName(state.sshPrivateKeyPath));
    }

    return result;
  }

  @NotNull
  public static String getSshTunnelValue(@Nullable String tunnelSetting) {
    tunnelSetting = !StringUtil.isEmpty(tunnelSetting) ? tunnelSetting : DEFAULT_SSH_TUNNEL_VALUE;
    String svnSshVariableName = getSvnSshVariableName(tunnelSetting);
    String svnSshVariableValue = EnvironmentUtil.getValue(svnSshVariableName);

    return !StringUtil.isEmpty(svnSshVariableValue)
           ? svnSshVariableValue
           : !StringUtil.isEmpty(svnSshVariableName) ? tunnelSetting.substring(1 + svnSshVariableName.length()) : tunnelSetting;
  }

  @NotNull
  public static String getSvnSshVariableName(@Nullable String tunnel) {
    String result = "";

    if (tunnel != null && tunnel.startsWith("$")) {
      result = ObjectUtils.notNull(StringUtil.substringBefore(tunnel, " "), tunnel).substring(1);
    }

    return result;
  }

  @NotNull
  public static String getExecutablePath(@Nullable String tunnelSetting) {
    // TODO: Add additional platform specific checks
    return StringUtil.notNullize(ContainerUtil.getFirstItem(ParametersListUtil.parse(getSshTunnelValue(tunnelSetting)))).trim();
  }
}
