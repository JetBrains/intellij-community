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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.swing.*;

// todo: migrate to EditorNotifications
public class MavenImportNotifier extends SimpleProjectComponent {
  private static final Key<NotifierPanel> PANEL_KEY = new Key<NotifierPanel>(NotifierPanel.class.getName());

  private final FileEditorManager myFileEditorManager;
  private final MavenProjectsManager myMavenProjectsManager;

  private final MergingUpdateQueue myUpdatesQueue;

  public MavenImportNotifier(Project p, FileEditorManager fileEditorManager, MavenProjectsManager mavenProjectsManager) {
    super(p);

    myFileEditorManager = fileEditorManager;
    myMavenProjectsManager = mavenProjectsManager;

    myUpdatesQueue = new MergingUpdateQueue(getComponentName(), 500, false, MergingUpdateQueue.ANY_COMPONENT, myProject);

    myMavenProjectsManager.addManagerListener(new MavenProjectsManager.Listener() {
      public void activated() {
        init();
        scheduleUpdate();
      }

      public void scheduledImportsChanged() {
        scheduleUpdate();
      }
    });
  }

  private void init() {
    myFileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        for (FileEditor each : source.getEditors(file)) {
          updateNotification(file, each);
        }
      }
    }, myProject);
    myUpdatesQueue.activate();
  }

  private void scheduleUpdate() {
    myUpdatesQueue.queue(new Update(myUpdatesQueue) {
      public void run() {
        updateNotifications();
      }
    });
  }

  private void updateNotifications() {
    for (VirtualFile f : myFileEditorManager.getOpenFiles()) {
      for (FileEditor e : myFileEditorManager.getEditors(f)) {
        updateNotification(f, e);
      }
    }
  }

  private void updateNotification(VirtualFile file, FileEditor editor) {
    if (myMavenProjectsManager.getImportingSettings().isImportAutomatically()
        || !myMavenProjectsManager.hasScheduledImports()) {
      JComponent panel = editor.getUserData(PANEL_KEY);
      if (panel == null) return;

      myFileEditorManager.removeTopComponent(editor, panel);
      editor.putUserData(PANEL_KEY, null);

      return;
    }

    MavenProject project = myMavenProjectsManager.findContainingProject(file);
    if (project == null) return;

    NotifierPanel panel = editor.getUserData(PANEL_KEY);
    if (panel == null) {
      panel = new NotifierPanel();
      editor.putUserData(PANEL_KEY, panel);
      myFileEditorManager.addTopComponent(editor, panel);
    }

    panel.update();
  }

  private class NotifierPanel extends EditorNotificationPanel {

    private NotifierPanel() {

      myLabel.setIcon(MavenIcons.MAVEN_ICON);

      createActionLabel(ProjectBundle.message("maven.project.import.changed"), new Runnable() {
        public void run() {
          myMavenProjectsManager.performScheduledImport();
        }
      });
      createActionLabel(ProjectBundle.message("maven.project.import.enable.auto"), new Runnable() {
        public void run() {
          myMavenProjectsManager.getImportingSettings().setImportAutomatically(true);
        }
      });
    }

    public void update() {
      int projectsCount = myMavenProjectsManager.getScheduledProjectsCount();
      String s;
      if (projectsCount == 0) {
        s = ProjectBundle.message("maven.project.something.changed");
      }
      else {
        s = ProjectBundle.message("maven.project.changed", projectsCount, projectsCount == 1 ? " is" : "s are");
      }
      myLabel.setText(s);
      myLabel.setToolTipText(s);
    }
  }
}
