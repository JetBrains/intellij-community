// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.jetbrains.idea.svn.auth.CertificateAuthenticationData;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;

/**
 * @author Konstantin Kolosovsky.
 */
public class TwoWaySslCallback extends UsernamePasswordCallback {

  private static final String ACCESS_TO_PREFIX = "Access to ";
  private static final String FORBIDDEN_STATUS = "forbidden";

  TwoWaySslCallback(@NotNull AuthenticationService authenticationService, Url url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    // https two-way protocol invalid client certificate
    return error.contains(ACCESS_TO_PREFIX) && error.contains(FORBIDDEN_STATUS);
  }

  @Override
  public String getType() {
    return SvnAuthenticationManager.SSL;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (myAuthentication instanceof CertificateAuthenticationData) {
      CertificateAuthenticationData auth = (CertificateAuthenticationData)myAuthentication;

      // TODO: Seems that config option should be specified for concrete server and not for global group.
      // as in that case it could be overriden by settings in config file
      command.put("--config-option");
      command.put("servers:global:ssl-client-cert-file=" + auth.getCertificatePath());
      command.put("--config-option");
      command.put("servers:global:ssl-client-cert-password=" + auth.getCertificatePassword());
      if (!auth.isStorageAllowed()) {
        command.put("--no-auth-cache");
      }
    }
  }
}
