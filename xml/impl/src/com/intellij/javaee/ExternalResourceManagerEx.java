package com.intellij.javaee;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * author: lesya
 */
public abstract class ExternalResourceManagerEx extends ExternalResourceManager {
  public static ExternalResourceManagerEx getInstanceEx(){
    return (ExternalResourceManagerEx)getInstance();
  }

  public abstract void removeResource(String url, @NotNull Project project);

  public abstract void addResource(@NonNls String url, @NonNls String location, @NotNull Project project);

  public abstract String[] getAvailableUrls();
  public abstract String[] getAvailableUrls(Project project);

  public abstract void clearAllResources();
  public abstract void clearAllResources(Project project);

  public abstract void addIgnoredResource(String url);
  public abstract void removeIgnoredResource(String url);

  public abstract boolean isIgnoredResource(String url);

  public abstract String[] getIgnoredResources();

  public abstract void addExternalResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);
}
