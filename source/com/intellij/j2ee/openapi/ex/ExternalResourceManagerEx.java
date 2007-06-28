package com.intellij.j2ee.openapi.ex;

import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.xml.impl.schema.XmlNSDescriptorImpl;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public abstract void addExteralResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);

  public abstract void addImplicitNamespace(@NotNull String ns, @NotNull XmlNSDescriptorImpl descriptor, Disposable parentDisposable);

  @Nullable
  public abstract XmlNSDescriptorImpl getImplicitNamespaceDescriptor(@NotNull String ns);
}
