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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProcessEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/4/13
 * Time: 4:58 PM
 */
public class SvnCommandFactory {
  public static SvnSimpleCommand createSimpleCommand(final Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    final SvnSimpleCommand command =
      new SvnSimpleCommand(workingDirectory, commandName, SvnApplicationSettings.getInstance().getCommandLinePath());
    addStartFailedListener(project, command);
    return command;
  }

  private static void addStartFailedListener(final Project project, SvnCommand command) {
    command.addListener(new ProcessEventListener() {
      @Override
      public void processTerminated(int exitCode) {
      }

      @Override
      public void startFailed(Throwable exception) {
        SvnVcs.getInstance(project).checkCommandLineVersion();
      }
    });
  }

  public static SvnLineCommand createLineCommand(Project project, File workingDirectory, @NotNull SvnCommandName commandName) {
    final SvnLineCommand command =
      new SvnLineCommand(workingDirectory, commandName, SvnApplicationSettings.getInstance().getCommandLinePath(), null);
    addStartFailedListener(project, command);
    return command;
  }
}
