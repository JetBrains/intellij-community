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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/31/12
 * Time: 3:02 PM
 */
public class SvnExecutableChecker extends ExecutableValidator {

  public SvnExecutableChecker(Project project) {
    super(project, getNotificationTitle(), getWrongPathMessage());
  }

  @Override
  protected String getCurrentExecutable() {
    return SvnApplicationSettings.getInstance().getCommandLinePath();
  }

  @NotNull
  @Override
  protected Configurable getConfigurable() {
    return getVcs().getConfigurable();
  }

  @NotNull
  private SvnVcs getVcs() {
    return SvnVcs.getInstance(myProject);
  }

  @Override
  protected void showSettingsAndExpireIfFixed(@NotNull Notification notification) {
    showSettings();
    // always expire notification as different message could be detected
    notification.expire();

    getVcs().checkCommandLineVersion();
  }

  @Override
  protected boolean isExecutableValid(@NotNull String executable) {
    setNotificationErrorDescription(getWrongPathMessage());

    final Version version = getVersion(executable);
    try {
      return version != null && validateVersion(version);
    }
    catch (Throwable e) {
      // do nothing
      return false;
    }
  }

  private boolean validateVersion(@NotNull Version version) {
    if (!version.is(1) || version.lessThan(1, 7)) {
      setNotificationErrorDescription(getOldExecutableMessage(version));
      return false;
    }

    WorkingCopyFormat format = getVcs().getWorkingCopyFormat(new File(myProject.getBaseDir().getPath()));
    if (!version.is(format.getVersion().major, format.getVersion().minor)) {
      setNotificationErrorDescription(getInconsistentExecutableMessage(version, format));
      return false;
    }
    // TODO: Show also "upgrade/convert" option if possible

    return true;
  }

  @Nullable
  public Version getVersion(@NotNull String executable) {
    Version result = null;

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      commandLine.addParameter("--version");
      commandLine.addParameter("--quiet");

      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput output = handler.runProcess(30 * 1000);

      if (!output.isTimeout() && (output.getExitCode() == 0) && output.getStderr().isEmpty()) {
        String versionText = output.getStdout().trim();
        final String[] parts = versionText.split("\\.");

        if (parts.length >= 3) {
          result = new Version(getInt(parts[0]), getInt(parts[1]), getInt(parts[2]));
        }
      }
    }
    catch (Throwable e) {
      // do nothing
    }

    return result;
  }

  private static int getInt(@NotNull String value) {
    return Integer.parseInt(value);
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

  private static String getInconsistentExecutableMessage(@NotNull Version version, @NotNull WorkingCopyFormat format) {
    return SvnBundle.message("subversion.executable.inconsistent.to.working.copy", version, format.getName());
  }
}
