// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:DependencyManagement interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:DependencyManagement documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomDependencyManagement extends MavenDomElement {

  /**
   * Returns the value of the dependencies child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:dependencies documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the dependencies child.
   */
  @NotNull
  MavenDomDependencies getDependencies();
}
