// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Relocation interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Relocation documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomRelocation extends MavenDomElement {

  /**
   * Returns the value of the groupId child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:groupId documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the groupId child.
   */
  @NotNull
  GenericDomValue<String> getGroupId();

  /**
   * Returns the value of the artifactId child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:artifactId documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the artifactId child.
   */
  @NotNull
  GenericDomValue<String> getArtifactId();

  /**
   * Returns the value of the version child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:version documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the version child.
   */
  @NotNull
  GenericDomValue<String> getVersion();

  /**
   * Returns the value of the message child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:message documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the message child.
   */
  @NotNull
  GenericDomValue<String> getMessage();
}
