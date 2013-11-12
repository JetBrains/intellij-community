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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import java.io.File;
import java.io.IOException;
import java.net.PasswordAuthentication;

/**
 * Passed for authentication purpose to SvnLineCommand
 * Called when svn command indicates that it needs credentials. It also means that credential was not found in standard place
 * (Subversion config directory)
 *
 * Implementations should 1) ask credential from user or take it from any other storage (memory, for instance)
 * 2) write credential in Subversion standard form into
 * a) standard config directory if user allowed to save *all* credentials
 * b) TMP directory and return path to the directory from getSpecialConfigDir() - if user rejected at least one credential storing
 *
 * Please note that several credentials could be asked during the command and therefore implementation class is used as
 * keeping its state, and that TMP directory should be reused for all written credentials
 *
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 1:05 PM
 */
public interface AuthenticationCallback {
  /**
   * Authenticate for realm and base file belonging to corresponding working copy
   *
   * @param realm - realm that should be used for credential retrieval/storage.
   * @param repositoryUrl
   * @param previousFailed - whether previous credentials were correct
   * @param passwordRequest - if true, password should be asked. Otherwise that may be a certificate (determined by the protocol)
   * @return false if authentication canceled or was unsuccessful
   */
  boolean authenticateFor(@Nullable String realm, SVNURL repositoryUrl, boolean previousFailed, boolean passwordRequest);

  /**
   * Provides authentication information to access given url by authentication protocol identified by type.
   * For instance, username/password for http/svn protocols. SSL client certificate for two way SSL protocol.
   *
   * @param repositoryUrl
   * @param type authentication protocol type with svn specific values, like "svn.simple" for http.
   * @return
   */
  @Nullable
  SVNAuthentication requestCredentials(SVNURL repositoryUrl, String type);

  /**
   * @return config directory if TMP was created
   */
  @Nullable
  File getSpecialConfigDir();

  @Nullable
  String requestSshCredentials(@NotNull String realm,
                               @NotNull SimpleCredentialsDialog.Mode mode,
                               @NotNull String key);

  /**
   * Ask user or read from memory storage whether server certificate should be accepted
   *
   * @param repositoryUrl
   * @param realm - realm that should be used for credential retrieval/storage.
   * @return true is certificate was accepted
   */
  boolean acceptSSLServerCertificate(SVNURL repositoryUrl, final String realm);

  /**
   * Clear credentials stored anywhere - in case they were not full, wrong or anything else
   *
   * @param realm - required that credential
   * @param repositoryUrl
   * @param password - whether password credential should be deleted or certificate, if protocol might demand certificate
   */
  void clearPassiveCredentials(String realm, SVNURL repositoryUrl, boolean password);

  /**
   * @return true if there's something from IDEA config that should be persisted into Subversion tmp config directory
   * for successful call
   * (now it's IDEA proxy settings)
   */
  boolean haveDataForTmpConfig();

  /**
   * writes IDEA config settings (that should be written) into tmp config directory
   * (now it's IDEA proxy settings)
   *
   * @param repositoryUrl
   * @return true if have written data, false if wasn't able to determine parameters etc
   * @throws IOException
   */
  boolean persistDataToTmpConfig(SVNURL repositoryUrl) throws IOException;

  @Nullable
  PasswordAuthentication getProxyAuthentication(@NotNull SVNURL repositoryUrl);

  /**
   * Ask for IDEA-defined proxy credentials, using standard authenticator
   * Store data into tmp config
   *
   * @param repositoryUrl
   * @return false if authentication was canceled or related calculations were unsuccessful
   */
  boolean askProxyCredentials(SVNURL repositoryUrl);

  void reset();
}
