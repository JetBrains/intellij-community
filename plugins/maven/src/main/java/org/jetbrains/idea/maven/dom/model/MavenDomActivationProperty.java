// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:ActivationProperty interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:ActivationProperty documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivationProperty extends MavenDomElement {

  /**
   * Returns the value of the name child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the name child.
   */
  @NotNull
  GenericDomValue<String> getName();

  /**
   * Returns the value of the value child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:value documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the value child.
   */
  @NotNull
  GenericDomValue<String> getValue();
}
