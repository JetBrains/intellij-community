// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:filtersElemType interface.
 */
public interface MavenDomFilters extends MavenDomElement {

  /**
   * Returns the list of filter children.
   *
   * @return the list of filter children.
   */
  @NotNull
  List<GenericDomValue<String>> getFilters();

  /**
   * Adds new child to the list of filter children.
   *
   * @return created child
   */
  GenericDomValue<String> addFilter();
}
