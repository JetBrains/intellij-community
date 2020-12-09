// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.*;

public final class MavenActionUtil {
  private MavenActionUtil() {
  }

  public static boolean hasProject(DataContext context) {
    return CommonDataKeys.PROJECT.getData(context) != null;
  }

  @Nullable
  public static Project getProject(DataContext context) {
    return CommonDataKeys.PROJECT.getData(context);
  }

  public static boolean isMavenizedProject(DataContext context) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) return false;
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstanceIfCreated(project);
    if (mavenProjectsManager == null) return false;
    return mavenProjectsManager.isMavenizedProject();
  }

  @Nullable
  public static MavenProject getMavenProject(DataContext context) {
    MavenProject result;
    final MavenProjectsManager manager = getProjectsManager(context);
    if (manager == null) return null;

    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (file != null) {
      result = manager.findProject(file);
      if (result != null) return result;
    }

    Module module = ExternalSystemActionUtil.getModule(context);
    if (module != null) {
      result = manager.findProject(module);
      if (result != null) return result;
    }

    return null;
  }

  @Nullable
  public static MavenProjectsManager getProjectsManager(DataContext context) {
    final Project project = getProject(context);
    if (project == null) return null;
    return MavenProjectsManager.getInstanceIfCreated(project);
  }

  public static boolean isMavenProjectFile(VirtualFile file) {
    return file != null && !file.isDirectory()
           && file.isInLocalFileSystem()
           && MavenUtil.isPomFile(file);
  }

  public static List<MavenProject> getMavenProjects(DataContext context) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) return Collections.emptyList();

    VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (virtualFiles == null || virtualFiles.length == 0) return Collections.emptyList();

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstanceIfCreated(project);
    if (projectsManager == null || !projectsManager.isMavenizedProject()) return Collections.emptyList();

    Set<MavenProject> res = new LinkedHashSet<>();

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    for (VirtualFile file : virtualFiles) {
      MavenProject mavenProject;

      if (file.isDirectory()) {
        VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
        if (!file.equals(contentRoot)) return Collections.emptyList();

        Module module = fileIndex.getModuleForFile(file);
        if (module == null || !projectsManager.isMavenizedModule(module)) return Collections.emptyList();

        mavenProject = projectsManager.findProject(module);
      }
      else {
        mavenProject = projectsManager.findProject(file);
      }

      if (mavenProject == null) return Collections.emptyList();

      res.add(mavenProject);
    }

    return new ArrayList<>(res);
  }

  public static List<VirtualFile> getMavenProjectsFiles(DataContext context) {
    return MavenUtil.collectFiles(getMavenProjects(context));
  }
}
