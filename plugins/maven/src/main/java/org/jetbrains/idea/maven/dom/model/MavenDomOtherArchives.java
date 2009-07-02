// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

import java.util.List;

/**
 * http://maven.apache.org/POM/4.0.0:otherArchivesElemType interface.
 */
public interface MavenDomOtherArchives extends MavenDomElement {

  /**
   * Returns the list of otherArchive children.
   *
   * @return the list of otherArchive children.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  List<GenericDomValue<String>> getOtherArchives();

  /**
   * Adds new child to the list of otherArchive children.
   *
   * @return created child
   */
  GenericDomValue<String> addOtherArchive();
}
