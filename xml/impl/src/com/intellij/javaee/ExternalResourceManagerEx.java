package com.intellij.javaee;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract void addImplicitNamespace(@NotNull String ns, @NotNull XmlNSDescriptor descriptor, Disposable parentDisposable);

  @Nullable
  public abstract XmlNSDescriptor getImplicitNamespaceDescriptor(@NotNull String ns);

}
