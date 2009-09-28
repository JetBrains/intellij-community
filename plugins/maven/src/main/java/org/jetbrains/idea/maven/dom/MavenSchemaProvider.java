package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class MavenSchemaProvider implements StandardResourceProvider {
  public static final String MAVEN_PROJECT_SCHEMA_URL = "http://maven.apache.org/xsd/maven-4.0.0.xsd";
  public static final String MAVEN_PROFILES_SCHEMA_URL = "http://maven.apache.org/xsd/profiles-1.0.0.xsd";
  public static final String MAVEN_SETTINGS_SCHEMA_URL = "http://maven.apache.org/xsd/settings-1.0.0.xsd";

  public void registerResources(ResourceRegistrar registrar) {
    registrar.addStdResource("http://maven.apache.org/maven-v4_0_0.xsd", "maven-4.0.0.xsd", getClass());
    registrar.addStdResource(MAVEN_PROJECT_SCHEMA_URL, "maven-4.0.0.xsd", getClass());

    registrar.addStdResource(MAVEN_PROFILES_SCHEMA_URL, "profiles-1.0.0.xsd", getClass());
    registrar.addStdResource(MAVEN_SETTINGS_SCHEMA_URL, "settings-1.0.0.xsd", getClass());
  }

  @NotNull
  public static VirtualFile getSchemaFile(@NotNull String url) {
    return VfsUtil.findRelativeFile(ExternalResourceManager.getInstance().getResourceLocation(url), null);
  }
}

