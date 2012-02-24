/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.config;

import java.util.Arrays;
import java.util.List;

public interface SvnServerFileKeys {
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

  List YES_OPTIONS = Arrays.asList(new String[]{"yes", "on", "true", "1"});
}
