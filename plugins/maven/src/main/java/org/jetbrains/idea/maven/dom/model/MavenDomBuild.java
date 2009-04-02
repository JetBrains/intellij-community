// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * http://maven.apache.org/POM/4.0.0:Build interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Build documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomBuild extends MavenDomBuildBase {

  /**
   * Returns the value of the sourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sourceDirectory documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the sourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getSourceDirectory();

  /**
   * Returns the value of the scriptSourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:scriptSourceDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the scriptSourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getScriptSourceDirectory();

  /**
   * Returns the value of the testSourceDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:testSourceDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the testSourceDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getTestSourceDirectory();

  /**
   * Returns the value of the outputDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:outputDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the outputDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getOutputDirectory();

  /**
   * Returns the value of the testOutputDirectory child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:testOutputDirectory documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the testOutputDirectory child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getTestOutputDirectory();

  /**
   * Returns the value of the extensions child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:extensions documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the extensions child.
   */
  @NotNull
  MavenDomExtensions getExtensions();
}
