/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.auth;

import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public interface SvnAuthenticationInteraction {
  void warnOnAuthStorageDisabled(final SVNURL url);
  void warnOnPasswordStorageDisabled(final SVNURL url);
  void warnOnSSLPassphraseStorageDisabled(final SVNURL url);
  boolean promptForSSLPlaintextPassphraseSaving(final SVNURL url, String realm, File certificateFile);
  boolean promptForPlaintextPasswordSaving(final SVNURL url, String realm);
  boolean promptInAwt();
}
