package com.intellij.j2ee.openapi.ex;

import com.intellij.j2ee.ExternalResourceManager;
import com.intellij.j2ee.extResources.ExternalResourceListener;

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

  public abstract boolean isIgnoredResource(String url);

  public abstract String[] getIgnoredResources();

  public abstract void addExteralResourceListener(ExternalResourceListener listener);

  public abstract void removeExternalResourceListener(ExternalResourceListener listener);
}
