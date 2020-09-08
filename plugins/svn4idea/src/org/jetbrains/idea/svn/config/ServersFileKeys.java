// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import java.util.Arrays;
import java.util.List;

public interface ServersFileKeys {
  String EXCEPTIONS = "http-proxy-exceptions";
  String SERVER = "http-proxy-host";
  String PORT = "http-proxy-port";
  String USER = "http-proxy-username";
  String PASSWORD = "http-proxy-password";
  String TIMEOUT = "http-timeout";

  // not used by svnkit jet
  String COMPRESSION = "http-compression";
  String LIBRARY = "http-library";
  String AUTH_TYPES = "http-auth-types";
  String NEON_DEBUG_MASK = "neon-debug-mask";

  String SSL_AUTHORITY_FILES = "ssl-authority-files";
  String SSL_TRUST_DEFAULT_CA = "ssl-trust-default-ca";
  String SSL_CLIENT_CERT_FILE = "ssl-client-cert-file";
  String SSL_CLIENT_CERT_PASSWORD = "ssl-client-cert-password";

  List YES_OPTIONS = Arrays.asList("yes", "on", "true", "1");
}
