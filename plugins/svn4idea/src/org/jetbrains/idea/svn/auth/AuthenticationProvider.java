// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import org.jetbrains.idea.svn.api.Url;

public interface AuthenticationProvider {
  AuthenticationData requestClientAuthentication(String kind, Url url, String realm, boolean canCache);

  AcceptResult acceptServerAuthentication(Url url, String realm, Object certificate, boolean canCache);
}
