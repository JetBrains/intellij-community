// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:ActivationOS interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:ActivationOS documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivationOS extends MavenDomElement {

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
   * Returns the value of the family child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:family documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the family child.
   */
  @NotNull
  GenericDomValue<String> getFamily();

  /**
   * Returns the value of the arch child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:arch documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the arch child.
   */
  @NotNull
  GenericDomValue<String> getArch();

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
}
