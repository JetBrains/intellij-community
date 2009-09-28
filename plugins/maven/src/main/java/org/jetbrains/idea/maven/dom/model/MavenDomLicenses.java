// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:licensesElemType interface.
 */
public interface MavenDomLicenses extends MavenDomElement {

  /**
   * Returns the list of license children.
   *
   * @return the list of license children.
   */
  @NotNull
  List<MavenDomLicense> getLicenses();

  /**
   * Adds new child to the list of license children.
   *
   * @return created child
   */
  MavenDomLicense addLicense();
}
