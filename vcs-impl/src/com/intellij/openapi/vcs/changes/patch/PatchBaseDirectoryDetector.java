package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PatchBaseDirectoryDetector {
  public static PatchBaseDirectoryDetector getInstance(Project project) {
    return ServiceManager.getService(project, PatchBaseDirectoryDetector.class);
  }

  @Nullable
  public abstract Result detectBaseDirectory(String name);

  public static class Result {
    public String baseDir;
    public int stripDirs;

    public Result(final String baseDir, final int stripDirs) {
      this.baseDir = baseDir;
      this.stripDirs = stripDirs;
    }
  }
}