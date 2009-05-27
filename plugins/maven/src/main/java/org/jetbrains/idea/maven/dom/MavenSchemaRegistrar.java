package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MavenSchemaRegistrar implements ApplicationComponent {
  public static final String MAVEN_SCHEMA_URL = "http://maven.apache.org/maven-v4_0_0.xsd";

  @NonNls
  @NotNull
  public String getComponentName() {
    return "MavenApplicationComponent";
  }

  public void initComponent() {
    ExternalResourceManager manager = ExternalResourceManager.getInstance();

    manager.addStdResource(MAVEN_SCHEMA_URL, "maven-v4_0_0.xsd", getClass());
    manager.addStdResource("http://maven.apache.org/xsd/maven-4.0.0.xsd", "maven-v4_0_0.xsd", getClass());
  }

  public void disposeComponent() {
  }
}

