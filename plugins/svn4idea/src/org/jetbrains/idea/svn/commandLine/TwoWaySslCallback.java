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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.auth.AuthenticationService;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;

/**
 * @author Konstantin Kolosovsky.
 */
public class TwoWaySslCallback extends UsernamePasswordCallback {

  private static final String ACCESS_TO_PREFIX = "Access to ";
  private static final String FORBIDDEN_STATUS = "forbidden";

  TwoWaySslCallback(@NotNull AuthenticationService authenticationService, SVNURL url) {
    super(authenticationService, url);
  }

  @Override
  public boolean canHandle(String error) {
    // https two-way protocol invalid client certificate
    return error.contains(ACCESS_TO_PREFIX) && error.contains(FORBIDDEN_STATUS);
  }

  @Override
  public String getType() {
    return ISVNAuthenticationManager.SSL;
  }

  @Override
  public void updateParameters(@NotNull Command command) {
    if (myAuthentication instanceof SVNSSLAuthentication) {
      SVNSSLAuthentication auth = (SVNSSLAuthentication)myAuthentication;

      // TODO: Seems that config option should be specified for concrete server and not for global group.
      // as in that case it could be overriden by settings in config file
      command.put("--config-option");
      command.put("servers:global:ssl-client-cert-file=" + auth.getCertificatePath());
      command.put("--config-option");
      command.put("servers:global:ssl-client-cert-password=" + auth.getPassword());
      if (!auth.isStorageAllowed()) {
        command.put("--no-auth-cache");
      }
    }
  }
}
