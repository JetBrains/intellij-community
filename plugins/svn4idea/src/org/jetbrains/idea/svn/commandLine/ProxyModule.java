/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.IdeaSVNConfigFile;
import org.jetbrains.idea.svn.SvnAuthenticationManager;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.tmatesoft.svn.core.SVNURL;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProxyModule extends BaseCommandRuntimeModule {

  private static final Logger LOG = Logger.getInstance(ProxyModule.class);

  public ProxyModule(@NotNull CommandRuntime runtime) {
    super(runtime);
  }

  @Override
  public void onStart(@NotNull Command command) throws SvnBindException {
    if (myAuthCallback.haveDataForTmpConfig()) {
      setupProxy(command);
    }
  }

  private void setupProxy(@NotNull Command command) {
    // TODO: We assume that if repository url is null - command is local and do not require repository access
    // TODO: Check if this is correct for all cases
    SVNURL repositoryUrl = command.getRepositoryUrl();

    if (repositoryUrl != null) {
      Proxy proxy = IdeaSvnkitBasedAuthenticationCallback.getIdeaDefinedProxy(repositoryUrl);

      if (proxy != null) {
        String hostGroup = ensureGroupForHost(command, repositoryUrl.getHost());
        InetSocketAddress address = (InetSocketAddress)proxy.address();

        command.put("--config-option");
        command.put(String.format("servers:%s:http-proxy-host=%s", hostGroup, address.getHostName()));
        command.put("--config-option");
        command.put(String.format("servers:%s:http-proxy-port=%s", hostGroup, address.getPort()));
      }
    } else {
      LOG.info("Configured proxy should be used, but repository url is null for command - " + command.getText());
    }
  }

  @NotNull
  private String ensureGroupForHost(@NotNull Command command, @NotNull String host) {
    IdeaSVNConfigFile configFile = new IdeaSVNConfigFile(myAuthCallback.getSpecialConfigDir());
    String groupName = SvnAuthenticationManager.getGroupForHost(host, configFile);

    if (StringUtil.isEmptyOrSpaces(groupName)) {
      groupName = SvnConfiguration.getNewGroupName(host, configFile);

      command.put("--config-option");
      command.put(String.format("servers:groups:%s=%s*", groupName, host));
    }

    return groupName;
  }
}
