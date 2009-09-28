// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Scm interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Scm documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomScm extends MavenDomElement {

  /**
   * Returns the value of the connection child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:connection documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the connection child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getConnection();

  /**
   * Returns the value of the developerConnection child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:developerConnection documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the developerConnection child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getDeveloperConnection();

  /**
   * Returns the value of the tag child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:tag documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the tag child.
   */
  @NotNull
  GenericDomValue<String> getTag();

  /**
   * Returns the value of the url child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:url documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the url child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUrl();
}
