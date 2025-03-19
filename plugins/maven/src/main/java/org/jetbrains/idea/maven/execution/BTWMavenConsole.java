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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * @deprecated Use MavenSyncConsole instead
 */
@Deprecated(forRemoval = true)
public class BTWMavenConsole extends MavenConsole {

  private final Project myProject;

  public BTWMavenConsole(Project project, MavenExecutionOptions.LoggingLevel outputLevel) {
    super(outputLevel);
    myProject = project;
  }

  @Override
  protected void doPrint(String text, OutputType type) {
    sendToSyncConsole(text, type == OutputType.NORMAL);
  }

  private void sendToSyncConsole(@NlsSafe String text, boolean stdout) {
    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(myProject).getSyncConsole();
    syncConsole.addText(text, stdout);
  }
}
