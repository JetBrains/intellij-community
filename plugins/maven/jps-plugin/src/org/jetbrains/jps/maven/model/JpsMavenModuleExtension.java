package org.jetbrains.jps.maven.model;

import org.jetbrains.jps.model.JpsElement;

import java.util.List;

public interface JpsMavenModuleExtension extends JpsElement {

  List<String> getAnnotationProcessorModules();
}
