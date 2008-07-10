package org.jetbrains.idea.maven.core;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Author: Vladislav.Kaznacheev
 */
public class MavenApplicationComponent implements ApplicationComponent {

  @NonNls
  @NotNull
  public String getComponentName() {
    return "MavenApplicationComponent";
  }

  public void initComponent() {
    ExternalResourceManager.getInstance().addStdResource("http://maven.apache.org/maven-v4_0_0.xsd","maven-v4_0_0.xsd",getClass());
    ExternalResourceManager.getInstance().addStdResource("http://maven.apache.org/xsd/maven-4.0.0.xsd","maven-v4_0_0.xsd",getClass());
  }

  public void disposeComponent() {
  }

}

