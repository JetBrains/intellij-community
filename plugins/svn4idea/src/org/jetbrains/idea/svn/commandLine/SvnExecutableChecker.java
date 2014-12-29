/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.ExecutableValidator;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.CmdVersionClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/31/12
 * Time: 3:02 PM
 */
public class SvnExecutableChecker extends ExecutableValidator {

  private static final Logger LOG = Logger.getInstance(SvnExecutableChecker.class);

  public static final String SVN_EXECUTABLE_LOCALE_REGISTRY_KEY = "svn.executable.locale";
  private static final String SVN_VERSION_ENGLISH_OUTPUT = "The following repository access (RA) modules are available";
  private static final Pattern INVALID_LOCALE_WARNING_PATTERN = Pattern.compile(
    "^.*cannot set .* locale.*please check that your locale name is correct$",
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

  @NotNull private final SvnVcs myVcs;

  public SvnExecutableChecker(@NotNull SvnVcs vcs) {
    super(vcs.getProject(), getNotificationTitle(), getWrongPathMessage());

    myVcs = vcs;
    Registry.get(SVN_EXECUTABLE_LOCALE_REGISTRY_KEY).addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        myVcs.checkCommandLineVersion();
      }
    }, myProject);
  }

  @Override
  protected String getCurrentExecutable() {
    return SvnApplicationSettings.getInstance().getCommandLinePath();
  }

  @NotNull
  @Override
  protected String getConfigurableDisplayName() {
    return SvnConfigurable.DISPLAY_NAME;
  }

  @Override
  protected boolean notify(@Nullable Notification notification) {
    expireAll();

    return super.notify(notification);
  }

  public void expireAll() {
    for (Notification notification : myNotificationManager.getNotificationsOfType(ExecutableNotValidNotification.class, myProject)) {
      notification.expire();
    }
  }

  @Override
  protected void showSettingsAndExpireIfFixed(@NotNull Notification notification) {
    showSettings();
    // always expire notification as different message could be detected
    notification.expire();

    myVcs.checkCommandLineVersion();
  }

  @Override
  @Nullable
  protected Notification validate(@NotNull String executable) {
    Notification result = createDefaultNotification();

    // Necessary executable path will be taken from settings while command execution
    final Version version = getConfiguredClientVersion();
    if (version != null) {
      try {
        result = validateVersion(version);

        if (result == null) {
          result = validateLocale();
        }
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }

    return result;
  }

  @Nullable
  private Notification validateVersion(@NotNull Version version) {
    return !myVcs.isSupportedByCommandLine(WorkingCopyFormat.from(version)) ? new ExecutableNotValidNotification(
      getOldExecutableMessage(version)) : null;
  }

  @Nullable
  private Notification validateLocale() throws SvnBindException {
    ProcessOutput versionOutput = getVersionClient().runCommand(false);
    Notification result = null;

    Matcher matcher = INVALID_LOCALE_WARNING_PATTERN.matcher(versionOutput.getStderr());
    if (matcher.find()) {
      LOG.info(matcher.group());

      result = new ExecutableNotValidNotification(prepareDescription(UIUtil.getHtmlBody(matcher.group()), false), NotificationType.WARNING);
    }
    else if (!isEnglishOutput(versionOutput.getStdout())) {
      LOG.info("\"svn --version\" command contains non-English output " + versionOutput.getStdout());

      result = new ExecutableNotValidNotification(prepareDescription(SvnBundle.message("non.english.locale.detected.warning"), false),
                                                  NotificationType.WARNING);
    }

    return result;
  }

  @Nullable
  private Version getConfiguredClientVersion() {
    Version result = null;

    try {
      result = getVersionClient().getVersion();
    }
    catch (Throwable e) {
      LOG.info(e);
    }

    return result;
  }

  @NotNull
  private CmdVersionClient getVersionClient() {
    return (CmdVersionClient)myVcs.getCommandLineFactory().createVersionClient();
  }

  public static boolean isEnglishOutput(@NotNull String versionOutput) {
    return StringUtil.containsIgnoreCase(versionOutput, SVN_VERSION_ENGLISH_OUTPUT);
  }

  private static String getWrongPathMessage() {
    return SvnBundle.message("subversion.executable.notification.description");
  }

  private static String getNotificationTitle() {
    return SvnBundle.message("subversion.executable.notification.title");
  }

  private static String getOldExecutableMessage(@NotNull Version version) {
    return SvnBundle.message("subversion.executable.too.old", version);
  }
}
