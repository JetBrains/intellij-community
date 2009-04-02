// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Profile interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Profile documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomProfile extends MavenDomElement {

  /**
   * Returns the value of the id child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:id documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the id child.
   */
  @NotNull
  @Required
  GenericDomValue<String> getId();

  /**
   * Returns the value of the activation child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:activation documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the activation child.
   */
  @NotNull
  MavenDomActivation getActivation();

  /**
   * Returns the value of the build child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:build documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the build child.
   */
  @NotNull
  MavenDomBuildBase getBuild();

  /**
   * Returns the value of the modules child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:modules documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the modules child.
   */
  @NotNull
  MavenDomModules getModules();

  /**
   * Returns the value of the repositories child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:repositories documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the repositories child.
   */
  @NotNull
  MavenDomRepositories getRepositories();

  /**
   * Returns the value of the pluginRepositories child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:pluginRepositories documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the pluginRepositories child.
   */
  @NotNull
  MavenDomPluginRepositories getPluginRepositories();

  /**
   * Returns the value of the dependencies child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:dependencies documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the dependencies child.
   */
  @NotNull
  MavenDomDependencies getDependencies();

  /**
   * Returns the value of the reports child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:reports documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the reports child.
   */
  @NotNull
  MavenDomReports getReports();

  /**
   * Returns the value of the reporting child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:reporting documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the reporting child.
   */
  @NotNull
  MavenDomReporting getReporting();

  /**
   * Returns the value of the dependencyManagement child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:dependencyManagement documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the dependencyManagement child.
   */
  @NotNull
  MavenDomDependencyManagement getDependencyManagement();

  /**
   * Returns the value of the distributionManagement child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:distributionManagement documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the distributionManagement child.
   */
  @NotNull
  MavenDomDistributionManagement getDistributionManagement();

  /**
   * Returns the value of the properties child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:properties documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the properties child.
   */
  @NotNull
  MavenDomProperties getProperties();
}
