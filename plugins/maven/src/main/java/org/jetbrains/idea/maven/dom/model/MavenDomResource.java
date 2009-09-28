// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Resource interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Resource documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomResource extends MavenDomElement {

  /**
   * Returns the value of the targetPath child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:targetPath documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the targetPath child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getTargetPath();

  /**
   * Returns the value of the filtering child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:filtering documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the filtering child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getFiltering();

  /**
   * Returns the value of the directory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:directory documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the directory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getDirectory();

  /**
   * Returns the value of the includes child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:includes documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the includes child.
   */
  @NotNull
  MavenDomIncludes getIncludes();

  /**
   * Returns the value of the excludes child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:excludes documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the excludes child.
   */
  @NotNull
  MavenDomExcludes getExcludes();
}
