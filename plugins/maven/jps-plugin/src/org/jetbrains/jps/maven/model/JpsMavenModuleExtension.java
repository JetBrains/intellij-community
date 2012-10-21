package org.jetbrains.jps.maven.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.maven.model.impl.MavenModuleExtensionProperties;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsMavenModuleExtension extends JpsElement {
  @NotNull
  MavenModuleExtensionProperties getState();

  void setState(MavenModuleExtensionProperties state);
}
