// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:extensionsElemType interface.
 */
public interface MavenDomExtensions extends MavenDomElement {

  /**
   * Returns the list of extension children.
   *
   * @return the list of extension children.
   */
  @NotNull
  List<MavenDomExtension> getExtensions();

  /**
   * Adds new child to the list of extension children.
   *
   * @return created child
   */
  MavenDomExtension addExtension();
}
