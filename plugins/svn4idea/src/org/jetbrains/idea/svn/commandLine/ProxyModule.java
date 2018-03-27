// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.IdeaSVNConfigFile;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * @author Konstantin Kolosovsky.
 */
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
      command.put(String.format("servers:%s:http-proxy-host=%s", hostGroup, address.getHostName()));
      command.put("--config-option");
      command.put(String.format("servers:%s:http-proxy-port=%s", hostGroup, address.getPort()));
    }
  }

  @NotNull
  private String ensureGroupForHost(@NotNull Command command, @NotNull String host) {
    IdeaSVNConfigFile configFile = new IdeaSVNConfigFile(myAuthenticationService.getSpecialConfigDir());
    String groupName = IdeaSVNConfigFile.getGroupForHost(host, configFile);

    if (StringUtil.isEmptyOrSpaces(groupName)) {
      groupName = IdeaSVNConfigFile.getNewGroupName(host, configFile);

      command.put("--config-option");
      command.put(String.format("servers:groups:%s=%s*", groupName, host));
    }

    return groupName;
  }
}
