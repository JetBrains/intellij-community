// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.config.SvnIniFile;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class ProxyModule extends BaseCommandRuntimeModule {

  public ProxyModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) {
    if (myAuthenticationService.haveDataForTmpConfig() && !CommandRuntime.isLocal(command)) {
      setupProxy(command);
    }
  }

  private void setupProxy(@NotNull Command command) {
    Url repositoryUrl = command.requireRepositoryUrl();
    Proxy proxy = AuthenticationService.getIdeaDefinedProxy(repositoryUrl);

    if (proxy != null) {
      String hostGroup = ensureGroupForHost(command, repositoryUrl.getHost());
      InetSocketAddress address = (InetSocketAddress)proxy.address();

      command.put("--config-option");
      command.put(String.format("servers:%s:http-proxy-host=%s", hostGroup, address.getHostString()));
      command.put("--config-option");
      command.put(String.format("servers:%s:http-proxy-port=%s", hostGroup, address.getPort()));
    }
  }

  private @NotNull String ensureGroupForHost(@NotNull Command command, @NotNull String host) {
    SvnIniFile configFile = new SvnIniFile(myAuthenticationService.getSpecialConfigDir());
    String groupName = SvnIniFile.getGroupForHost(host, configFile);

    if (StringUtil.isEmptyOrSpaces(groupName)) {
      groupName = SvnIniFile.getNewGroupName(host, configFile);

      command.put("--config-option");
      command.put(String.format("servers:groups:%s=%s*", groupName, host));
    }

    return groupName;
  }
}
