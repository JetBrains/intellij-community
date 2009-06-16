package org.jetbrains.idea.maven.dom;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

public class MavenSchemaRegistrar implements StandardResourceProvider {

  public static final String MAVEN_SCHEMA_URL = "http://maven.apache.org/maven-v4_0_0.xsd";

  public void registerResources(ResourceRegistrar registrar) {
    registrar.addStdResource(MAVEN_SCHEMA_URL, "maven-v4_0_0.xsd", getClass());
    registrar.addStdResource("http://maven.apache.org/xsd/maven-4.0.0.xsd", "maven-v4_0_0.xsd", getClass());
  }
}

