package com.intellij.platform;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlatformVcsDetector implements ProjectComponent {
  private Project myProject;
  private ProjectLevelVcsManagerImpl myVcsManager;

  public PlatformVcsDetector(final Project project, final ProjectLevelVcsManagerImpl vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerStartupActivity(new Runnable() {
      public void run() {
        VirtualFile file = PlatformProjectOpenProcessor.getBaseDir(myProject.getBaseDir());
        AbstractVcs vcs = myVcsManager.findVersioningVcs(file);
        if (vcs != null && vcs != myVcsManager.getVcsFor(file)) {
          myVcsManager.setAutoDirectoryMapping(file.getPath(), vcs.getName());
          myVcsManager.cleanupMappings();
          myVcsManager.updateActiveVcss();
        }
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "PlatformVcsDetector";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
