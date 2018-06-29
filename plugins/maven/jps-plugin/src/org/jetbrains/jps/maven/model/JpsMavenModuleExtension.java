package org.jetbrains.jps.maven.model;

import org.jetbrains.jps.model.JpsElement;

import java.util.List;

/**
 * @author nik
 */
public interface JpsMavenModuleExtension extends JpsElement {

  List<String> getAnnotationProcessorModules();
}
