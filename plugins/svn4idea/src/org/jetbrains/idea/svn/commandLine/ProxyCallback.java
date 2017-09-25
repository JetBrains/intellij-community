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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNURL;

import java.net.PasswordAuthentication;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProxyCallback extends AuthCallbackCase {

  private static final Logger LOG = Logger.getInstance(ProxyCallback.class);

  private static final String CANNOT_AUTHENTICATE_TO_PROXY = "Could not authenticate to proxy server";
  private static final String PROXY_AUTHENTICATION_FAILED = "Proxy authentication failed";

  private PasswordAuthentication myProxyAuthentication;

  ProxyCallback(@NotNull AuthenticationService authenticationService, SVNURL url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    return
      // svn 1.7 proxy error message
      error.contains(CANNOT_AUTHENTICATE_TO_PROXY) ||
      // svn 1.8 proxy error message
      error.contains(PROXY_AUTHENTICATION_FAILED);
  }

  @Override
  boolean getCredentials(String errText) {
    boolean result = false;

    if (myUrl == null) {
      // TODO: We assume that if repository url is null - command is local and do not require repository access
      // TODO: Check if this is correct for all cases
      LOG.info("Proxy callback could handle error text, but repository url is null", new Throwable());

      result = true;
      // explicit check if proxies are configured in IDEA is used here not to perform "proxy authentication" for proxies manually
      // specified by users in svn "servers" file
    } else if (myAuthenticationService.haveDataForTmpConfig()) {
      myProxyAuthentication = myAuthenticationService.getProxyAuthentication(myUrl);
      result = myProxyAuthentication != null;
    }

    return result;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    // TODO: This is quite messy logic for determining group for host - either ProxyCallback could be unified with ProxyModule
    // TODO: or group name resolved in ProxyModule could be saved in Command instance.
    // TODO: This will be done later after corresponding refactorings.
    String proxyHostParameter = ContainerUtil.find(command.getParameters(), s -> s.contains("http-proxy-port"));

    if (!StringUtil.isEmpty(proxyHostParameter) && myUrl != null && myProxyAuthentication != null) {
      String group = getHostGroup(proxyHostParameter);

      command.put("--config-option");
      command.put(String.format("servers:%s:http-proxy-username=%s", group, myProxyAuthentication.getUserName()));
      command.put("--config-option");
      command.put(String.format("servers:%s:http-proxy-password=%s", group, String.valueOf(myProxyAuthentication.getPassword())));
    }
  }

  @NotNull
  private static String getHostGroup(@NotNull String proxyHostParameter) {
    int start = proxyHostParameter.indexOf(":");
    int finish = proxyHostParameter.indexOf(":", start + 1);

    return proxyHostParameter.substring(start + 1, finish);
  }
}
