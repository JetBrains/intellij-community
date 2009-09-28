// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:pluginRepositoriesElemType interface.
 */
public interface MavenDomPluginRepositories extends MavenDomElement {

  /**
   * Returns the list of pluginRepository children.
   *
   * @return the list of pluginRepository children.
   */
  @NotNull
  List<MavenDomRepository> getPluginRepositories();

  /**
   * Adds new child to the list of pluginRepository children.
   *
   * @return created child
   */
  MavenDomRepository addPluginRepository();
}
