// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:mailingListsElemType interface.
 */
public interface MavenDomMailingLists extends MavenDomElement {

  /**
   * Returns the list of mailingList children.
   *
   * @return the list of mailingList children.
   */
  @NotNull
  List<MavenDomMailingList> getMailingLists();

  /**
   * Adds new child to the list of mailingList children.
   *
   * @return created child
   */
  MavenDomMailingList addMailingList();
}
