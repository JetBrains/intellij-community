// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomProjectModelBase extends MavenDomElement {
  @NotNull
  MavenDomBuildBase getBuild();

  @NotNull
  MavenDomModules getModules();

  @NotNull
  MavenDomRepositories getRepositories();

  @NotNull
  MavenDomPluginRepositories getPluginRepositories();

  @NotNull
  MavenDomDependencies getDependencies();

  @NotNull
  MavenDomReports getReports();

  @NotNull
  MavenDomReporting getReporting();

  @NotNull
  MavenDomDependencyManagement getDependencyManagement();

  @NotNull
  MavenDomDistributionManagement getDistributionManagement();

  @NotNull
  MavenDomProperties getProperties();
}