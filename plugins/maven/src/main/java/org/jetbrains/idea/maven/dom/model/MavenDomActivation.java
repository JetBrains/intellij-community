// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Activation interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Activation documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivation extends MavenDomElement {

  /**
   * Returns the value of the activeByDefault child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:activeByDefault documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the activeByDefault child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getActiveByDefault();

  /**
   * Returns the value of the jdk child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:jdk documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the jdk child.
   */
  @NotNull
  GenericDomValue<String> getJdk();

  /**
   * Returns the value of the os child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:os documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the os child.
   */
  @NotNull
  MavenDomActivationOS getOs();

  /**
   * Returns the value of the property child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:property documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the property child.
   */
  @NotNull
  MavenDomActivationProperty getProperty();

  /**
   * Returns the value of the file child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:file documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the file child.
   */
  @NotNull
  MavenDomActivationFile getFile();
}
