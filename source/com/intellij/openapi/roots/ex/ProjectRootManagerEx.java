package com.intellij.openapi.roots.ex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;

import java.util.EventListener;

public abstract class ProjectRootManagerEx extends ProjectRootManager {
  public static ProjectRootManagerEx getInstanceEx(Project project) {
    return (ProjectRootManagerEx)getInstance(project);
  }

  public abstract void setLanguageLevel(LanguageLevel level);

  public abstract LanguageLevel getLanguageLevel();

  public abstract void registerChangeUpdater(CacheUpdater updater);

  public abstract void unregisterChangeUpdater(CacheUpdater updater);

  public abstract void addProjectJdkListener(ProjectJdkListener listener);

  public abstract void removeProjectJdkListener(ProjectJdkListener listener);

  public abstract void beforeRootsChange(boolean filetypes);

  public abstract void rootsChanged(boolean filetypes);


  public interface ProjectJdkListener extends EventListener {
    void projectJdkChanged();
  }
}
