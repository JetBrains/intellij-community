// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class MavenSchemaProvider implements StandardResourceProvider {
  public static final String MAVEN_PROJECT_SCHEMA_URL = "http://maven.apache.org/xsd/maven-4.0.0.xsd";
  public static final String MAVEN_PROFILES_SCHEMA_URL = "http://maven.apache.org/xsd/profiles-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL_1_1 = "http://maven.apache.org/xsd/settings-1.1.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL_1_2 = "http://maven.apache.org/xsd/settings-1.2.0.xsd";

  @Override
  public void registerResources(ResourceRegistrar registrar) {
    @SuppressWarnings("HttpUrlsUsage") String[] urls = new String[]{
      MAVEN_PROJECT_SCHEMA_URL, "schemas/maven-4.0.0.xsd",
      "http://maven.apache.org/maven-v4_0_0.xsd", "schemas/maven-4.0.0.xsd",
      MAVEN_PROFILES_SCHEMA_URL, "schemas/profiles-1.0.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL, "schemas/settings-1.0.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL_1_1, "schemas/settings-1.1.0.xsd",
      MAVEN_SETTINGS_SCHEMA_URL_1_2, "schemas/settings-1.2.0.xsd"
    };
    for (int i = 0; i < urls.length; i += 2) {
      addStdResource(registrar, urls[i], urls[i + 1]);
    }
  }

  @SuppressWarnings("HttpUrlsUsage")
  private void addStdResource(ResourceRegistrar registrar, String schemaUrl, String schemaPath) {
    ClassLoader classLoader = getClass().getClassLoader();
    registrar.addStdResource(schemaUrl, schemaPath, classLoader);
    if (schemaUrl.startsWith("http://")) {
      registrar.addStdResource(schemaUrl.replace("http://", "https://"), schemaPath, classLoader);
    }
  }

  @NotNull
  public static VirtualFile getSchemaFile(@NotNull String url) {
    String location = ((ExternalResourceManagerEx)ExternalResourceManager.getInstance()).getStdResource(url, null);
    assert location != null : "cannot find a standard resource for " + url;

    VirtualFile result = VfsUtilCore.findRelativeFile(location, null);
    assert result != null : "cannot find a schema file for URL: " + url + " location: " + location;

    return result;
  }
}

