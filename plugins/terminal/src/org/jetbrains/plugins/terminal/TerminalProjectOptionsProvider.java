/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author traff
 */
@State(
  name = "TerminalProjectOptionsProvider",
  storages = @Storage("terminal.xml")
)
public class TerminalProjectOptionsProvider implements PersistentStateComponent<TerminalProjectOptionsProvider.State> {
  private static final Logger LOG = Logger.getInstance(TerminalProjectOptionsProvider.class);

  private State myState = new State();
  private final Project myProject;

  public TerminalProjectOptionsProvider(@NotNull  Project project) {myProject = project;}


  public static TerminalProjectOptionsProvider getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalProjectOptionsProvider.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    setShellPath(state.myShellPath);
    myState.myStartingDirectory = state.myStartingDirectory;
  }


  public static class State {
    public String myShellPath = null;
    public String myStartingDirectory = null;
  }

  public String getShellPath() {
    if (myState.myShellPath != null) {
      return myState.myShellPath;
    } else {
      return getDefaultShellPath();
    }
  }


  public void setShellPath(String shellPath) {
    if (isShellPathDefault(shellPath) || StringUtil.isEmpty(shellPath)) {
      myState.myShellPath = null;
    } else {
      myState.myShellPath = shellPath;
    }
  }


  public void setStartingDirectory(String startingDirectory) {
    if (isStartingDirectoryDefault(startingDirectory) || StringUtil.isEmpty(startingDirectory)) {
      myState.myStartingDirectory = null;
    } else {
      myState.myStartingDirectory = startingDirectory;
    }
  }

  public boolean isShellPathDefault(String shellPath) {
    return StringUtil.equals(shellPath, getDefaultShellPath());
  }

  public boolean isStartingDirectoryDefault(String startingDirectory) {
    return StringUtil.equals(startingDirectory, getDefaultStartingDirectory());
  }

  public String getStartingDirectory() {
    if (myState.myStartingDirectory != null) {
      return myState.myStartingDirectory;
    }
    else {
      return getDefaultStartingDirectory();
    }
  }


  private static String getDefaultShellPath() {
    String shell = System.getenv("SHELL");

    if (shell != null && new File(shell).canExecute()) {
      return shell;
    }

    if (SystemInfo.isUnix) {
      if (new File("/bin/bash").exists()) {
        return "/bin/bash";
      }
      else {
        return "/bin/sh";
      }
    }
    else {
      return "cmd.exe";
    }
  }

  public String getDefaultStartingDirectory() {
    String directory = null;
    for (LocalTerminalCustomizer customizer : LocalTerminalCustomizer.EP_NAME.getExtensions()) {
      try {

        if (directory == null) {
          directory = customizer.getDefaultFolder();
        }
      }
      catch (Exception e) {
        LOG.error("Exception during getting default folder", e);
      }
    }

    return currentProjectFolder();
  }


  private String currentProjectFolder() {
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);

    final VirtualFile[] roots = projectRootManager.getContentRoots();
    if (roots.length == 1) {
      roots[0].getCanonicalPath();
    }
    final VirtualFile baseDir = myProject.getBaseDir();
    return baseDir == null ? null : baseDir.getCanonicalPath();
  }
}

