// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

public interface ServersFileKeys {
  @NlsSafe String GLOBAL_SERVER_GROUP = "global";
  @NonNls String SERVER_GROUPS_SECTION = "groups";

  @NonNls String EXCEPTIONS = "http-proxy-exceptions";
  @NonNls String SERVER = "http-proxy-host";
  @NonNls String PORT = "http-proxy-port";
  @NonNls String USER = "http-proxy-username";
  @NonNls String PASSWORD = "http-proxy-password";
  @NonNls String TIMEOUT = "http-timeout";

  @NonNls String SSL_AUTHORITY_FILES = "ssl-authority-files";
  @NonNls String SSL_TRUST_DEFAULT_CA = "ssl-trust-default-ca";
  @NonNls String SSL_CLIENT_CERT_FILE = "ssl-client-cert-file";
  @NonNls String SSL_CLIENT_CERT_PASSWORD = "ssl-client-cert-password";
}
