// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenRepositoryChecksumPolicyConverter;
import org.jetbrains.idea.maven.dom.converters.MavenRepositoryUpdatePolicyConverter;

/**
 * http://maven.apache.org/POM/4.0.0:RepositoryPolicy interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:RepositoryPolicy documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomRepositoryPolicy extends MavenDomElement {

  /**
   * Returns the value of the enabled child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:enabled documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the enabled child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getEnabled();

  /**
   * Returns the value of the updatePolicy child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:updatePolicy documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the updatePolicy child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenRepositoryUpdatePolicyConverter.class)
  GenericDomValue<String> getUpdatePolicy();

  /**
   * Returns the value of the checksumPolicy child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:checksumPolicy documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the checksumPolicy child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenRepositoryChecksumPolicyConverter.class)
  GenericDomValue<String> getChecksumPolicy();
}
