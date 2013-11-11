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
package org.jetbrains.plugins.terminal.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * @author traff
 */
public class TerminalSessionsVFS extends DummyFileSystem {
  @NonNls private static final String PROTOCOL = "terminalDummy";
  @NonNls private static final String PATH_PREFIX = "terminal";
  @NonNls private static final String PROTOCOL_SEPARATOR = ":/";
  
  private final BidirectionalMap<Project, String> myProject2Id = new BidirectionalMap<Project, String>();
  private final Map<String, VirtualFile> myCachedFiles = new HashMap<String, VirtualFile>();

  private ProjectManagerAdapter myProjectManagerListener;

  public static TerminalSessionsVFS getTerminalSessionVFS() {
    return (TerminalSessionsVFS)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Override
  @Nullable
  public VirtualFile createRoot(String name) {
    return null;
  }

  public void initListener() {
    if (myProjectManagerListener == null || ApplicationManager.getApplication().isUnitTestMode()) {
      myCachedFiles.clear();
      myProject2Id.clear();
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        onProjectOpened(project);
      }
    }
    if (myProjectManagerListener == null) {
      myProjectManagerListener = new ProjectManagerAdapter() {
        @Override
        public void projectOpened(final Project project) {
          onProjectOpened(project);
        }

        @Override
        public void projectClosed(final Project project) {
          onProjectClosed(project);
        }
      };
      ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
    }
  }

  public void onProjectClosed(final Project project) {
    myCachedFiles.clear();
    myProject2Id.remove(project);
  }

  public void onProjectOpened(final Project project) {
    myProject2Id.put(project, project.getLocationHash());
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
//        DatabaseEditorHelper.installEditorFactoryListener(project); TODO:? 
      }
    });
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    throw new UnsupportedOperationException("not implemented yet"); //TODO: implement TerminalSessionManager and store there terminal sessions by handle ID
  }

  public static String getPath(Project project, final String dataSourceId, final String tableName, String typeName) {
    return PATH_PREFIX + typeName + PROTOCOL_SEPARATOR + project.getLocationHash() + "/" + dataSourceId + "/" + tableName;
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new UnsupportedOperationException("renameFile not supported");
  }

  @Override
  @NotNull
  public String extractPresentableUrl(@NotNull String path) {
    VirtualFile file = findFileByPath(path);
    return file != null ? file.getPresentableName() : super.extractPresentableUrl(path);
  }
}
