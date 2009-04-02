// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:modulesElemType interface.
 */
public interface MavenDomModules extends MavenDomElement {

  /**
   * Returns the list of module children.
   *
   * @return the list of module children.
   */
  @NotNull
  List<MavenDomModule> getModules();

  /**
   * Adds new child to the list of module children.
   *
   * @return created child
   */
  MavenDomModule addModule();
}
