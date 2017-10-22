// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.PasswordAuthenticationData;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalUserNamePasswordModule extends BaseTerminalModule {

  private static final Pattern USER_NAME_PROMPT = Pattern.compile("Username:\\s?");
  private static final Pattern PASSWORD_PROMPT = Pattern.compile("Password for \\'(.*)\\':\\s?");

  private static final Pattern AUTHENTICATION_REALM_MESSAGE = Pattern.compile("Authentication realm: (.*)\\s?");

  private String realm;
  private String userName;
  private PasswordAuthenticationData authentication;

  public TerminalUserNamePasswordModule(@NotNull CommandRuntime runtime, @NotNull CommandExecutor executor) {
    super(runtime, executor);
  }

  @Override
  public boolean doHandlePrompt(String line, Key outputType) {
    return checkRealm(line) || checkUserName(line) || checkPassword(line);
  }

  private boolean checkRealm(@NotNull String line) {
    Matcher matcher = AUTHENTICATION_REALM_MESSAGE.matcher(line);

    if (matcher.matches()) {
      realm = matcher.group(1);
    }

    return matcher.matches();
  }

  private boolean checkUserName(@NotNull String line) {
    Matcher matcher = USER_NAME_PROMPT.matcher(line);

    return matcher.matches() && handleAuthPrompt(true);
  }

  private boolean checkPassword(@NotNull String line) {
    Matcher matcher = PASSWORD_PROMPT.matcher(line);

    if (matcher.matches()) {
      userName = matcher.group(1);
    }

    return matcher.matches() && handleAuthPrompt(false);
  }

  /**
   * User name and password are asked separately by svn, but we show single dialog for both parameters. Also password could be asked first
   * (before any user name prompt) for pre-configured/system user name.
   */
  private boolean handleAuthPrompt(boolean isUserName) {
    Url repositoryUrl = myExecutor.getCommand().requireRepositoryUrl();

    if (needAskAuthentication(isUserName)) {
      // TODO: Probably pass real realm to dialog
      // TODO: Extend interface to pass username to dialog (probably using some kind of previousAuth, like in SVNKit)
      authentication = (PasswordAuthenticationData)myRuntime.getAuthenticationService()
        .requestCredentials(repositoryUrl, SvnAuthenticationManager.PASSWORD);
    }

    return sendData(isUserName);
  }

  private boolean needAskAuthentication(boolean isUserName) {
    // "authentication.getUserName()" was provided by user, "userName" was asked by svn. If they are equal but svn still prompts user name -
    // we treat this as "authentication" credentials are incorrect and we need ask user again.
    return authentication == null || isUserName && StringUtil.equals(userName, authentication.getUserName());
  }

  private boolean sendData(boolean isUserName) {
    if (authentication != null) {
      sendData(isUserName ? authentication.getUserName() : authentication.getPassword());
    }
    else {
      cancelAuthentication();
    }

    return authentication != null;
  }
}
