package com.intellij.javaee;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.Collection;

/**
 * author: lesya
 */
public abstract class ExternalResourceManagerEx extends ExternalResourceManager {
  public static ExternalResourceManagerEx getInstanceEx(){
    return (ExternalResourceManagerEx)getInstance();
  }

  public abstract String[] getAvailableUrls();

  public abstract void clearAllResources();

  public abstract void addIgnoredResource(String url);
  public abstract void removeIgnoredResource(String url);

  public abstract boolean isIgnoredResource(String url);

  public abstract String[] getIgnoredResources();

  public abstract void addExternalResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);

  public abstract void addImplicitNamespace(@NotNull String ns, @NotNull XmlNSDescriptorImpl descriptor, Disposable parentDisposable);

  @Nullable
  public abstract XmlNSDescriptorImpl getImplicitNamespaceDescriptor(@NotNull String ns);

  public abstract Set<String> getNamespacesByTagName(String tagName, Project project);
  
  public abstract Collection<VirtualFile> getSchemaFiles(String namespace, Project project);

}
