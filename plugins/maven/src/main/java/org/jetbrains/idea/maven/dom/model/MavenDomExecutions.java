// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:executionsElemType interface.
 */
public interface MavenDomExecutions extends MavenDomElement {

  /**
   * Returns the list of execution children.
   *
   * @return the list of execution children.
   */
  @NotNull
  List<MavenDomPluginExecution> getExecutions();

  /**
   * Adds new child to the list of execution children.
   *
   * @return created child
   */
  MavenDomPluginExecution addExecution();
}
