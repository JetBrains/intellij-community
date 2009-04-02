// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:reportSetsElemType interface.
 */
public interface MavenDomReportSets extends MavenDomElement {

  /**
   * Returns the list of reportSet children.
   *
   * @return the list of reportSet children.
   */
  @NotNull
  List<MavenDomReportSet> getReportSets();

  /**
   * Adds new child to the list of reportSet children.
   *
   * @return created child
   */
  MavenDomReportSet addReportSet();
}
