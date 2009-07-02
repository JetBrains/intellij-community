// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:License interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:License documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomLicense extends MavenDomElement {

  /**
   * Returns the value of the name child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the name child.
   */
  @NotNull
  GenericDomValue<String> getName();

  /**
   * Returns the value of the url child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:url documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the url child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUrl();

  /**
   * Returns the value of the distribution child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:distribution documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the distribution child.
   */
  @NotNull
  GenericDomValue<String> getDistribution();

  /**
   * Returns the value of the comments child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:comments documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the comments child.
   */
  @NotNull
  GenericDomValue<String> getComments();
}
