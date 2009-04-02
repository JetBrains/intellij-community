// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:excludesElemType interface.
 */
public interface MavenDomExcludes extends MavenDomElement {

  /**
   * Returns the list of exclude children.
   *
   * @return the list of exclude children.
   */
  @NotNull
  List<GenericDomValue<String>> getExcludes();

  /**
   * Adds new child to the list of exclude children.
   *
   * @return created child
   */
  GenericDomValue<String> addExclude();
}
