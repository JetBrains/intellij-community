// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.ExecutableValidator;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnConfigurable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.CmdVersionClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.idea.svn.SvnBundle.message;

@Service
public final class SvnExecutableChecker extends ExecutableValidator implements Disposable {
  private static final Logger LOG = Logger.getInstance(SvnExecutableChecker.class);

  public static final String SVN_EXECUTABLE_LOCALE_REGISTRY_KEY = "svn.executable.locale";
  private static final @NonNls String SVN_VERSION_ENGLISH_OUTPUT = "The following repository access (RA) modules are available";
  private static final Pattern INVALID_LOCALE_WARNING_PATTERN = Pattern.compile(
    "^.*cannot set .* locale.*please check that your locale name is correct$",
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

  @NotNull private final SvnVcs myVcs;

  public SvnExecutableChecker(@NotNull Project project) {
    super(project,
          message("subversion.executable.notification.title"),
          message("subversion.executable.notification.description"),
          message("subversion.executable.notification.cant.run.in.safe.mode"));

    myVcs = SvnVcs.getInstance(project);
    Registry.get(SVN_EXECUTABLE_LOCALE_REGISTRY_KEY).addListener(new RegistryValueListener() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        myVcs.checkCommandLineVersion();
      }
    }, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  protected String getCurrentExecutable() {
    return SvnApplicationSettings.getInstance().getCommandLinePath();
  }

  @NotNull
  @Override
  protected String getConfigurableDisplayName() {
    return SvnConfigurable.getGroupDisplayName();
  }

  @Override
  protected boolean notify(@Nullable Notification notification) {
    expireAll();

    return super.notify(notification);
  }

  public void expireAll() {
    for (Notification notification : NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(ExecutableNotValidNotification.class, myProject)) {
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
    return !myVcs.isSupportedByCommandLine(WorkingCopyFormat.from(version))
           ? new ExecutableNotValidNotification(message("subversion.executable.too.old", version))
           : null;
  }

  @Nullable
  private Notification validateLocale() throws SvnBindException {
    ProcessOutput versionOutput = getVersionClient().runCommand(false);
    Notification result = null;

    Matcher matcher = INVALID_LOCALE_WARNING_PATTERN.matcher(versionOutput.getStderr());
    if (matcher.find()) {
      @NlsSafe String warningText = matcher.group();
      LOG.info(warningText);

      result = new ExecutableNotValidNotification(prepareDescription(UIUtil.getHtmlBody(warningText), false), NotificationType.WARNING);
    }
    else if (!isEnglishOutput(versionOutput.getStdout())) {
      LOG.info("\"svn --version\" command contains non-English output " + versionOutput.getStdout());

      result = new ExecutableNotValidNotification(prepareDescription(message("non.english.locale.detected.warning"), false),
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
}
