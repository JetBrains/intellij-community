// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;

import java.net.PasswordAuthentication;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProxyCallback extends AuthCallbackCase {

  private static final Logger LOG = Logger.getInstance(ProxyCallback.class);

  private static final String CANNOT_AUTHENTICATE_TO_PROXY = "Could not authenticate to proxy server";
  private static final String PROXY_AUTHENTICATION_FAILED = "Proxy authentication failed";

  private PasswordAuthentication myProxyAuthentication;

  ProxyCallback(@NotNull AuthenticationService authenticationService, Url url) {
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
