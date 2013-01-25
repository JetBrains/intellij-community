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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;

import java.text.MessageFormat;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/31/12
 * Time: 3:02 PM
 */
public class SvnExecutableChecker extends ExecutableValidator {
  private final static String ourPath = "Probably the path to Subversion executable is wrong.";
  private static final String ourVersion = "Subversion command line client version is too old ({0}).";
  
  public SvnExecutableChecker(Project project) {
    super(project, "Can't use Subversion command line client", ourPath);
  }

  @Override
  protected String getCurrentExecutable() {
    return SvnApplicationSettings.getInstance().getCommandLinePath();
  }

  @NotNull
  @Override
  protected Configurable getConfigurable() {
    return SvnVcs.getInstance(myProject).getConfigurable();
  }

  @Override
  protected boolean isExecutableValid(@NotNull String executable) {
    setNotificationErrorDescription(ourPath);
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      commandLine.addParameter("--version");
      commandLine.addParameter("--quiet");
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine.createProcess(), CharsetToolkit.getDefaultSystemCharset());
      ProcessOutput result = handler.runProcess(30 * 1000);
      if (! result.isTimeout() && (result.getExitCode() == 0) && result.getStderr().isEmpty()) {
        final String stdout = result.getStdout().trim();
        final String[] parts = stdout.split("\\.");
        if (parts.length < 3 || ! "1".equals(parts[0])) {
          setNotificationErrorDescription(MessageFormat.format(ourVersion, stdout));
          return false;
        }
        try {
          final int second = Integer.parseInt(parts[1]);
          if (second >= 7) return true;
        } catch (NumberFormatException e) {
          //
        }
        setNotificationErrorDescription(MessageFormat.format(ourVersion, stdout));
        return false;
      } else {
        return false;
      }
    } catch (Throwable e) {
      return false;
    }
  }
}
