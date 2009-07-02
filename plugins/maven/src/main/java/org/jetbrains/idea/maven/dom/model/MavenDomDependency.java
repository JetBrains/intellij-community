// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.psi.PsiFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyScopeConverter;
import org.jetbrains.idea.maven.dom.converters.MavenDependencySystemPathConverter;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenPackagingTypeConverter;

/**
 * http://maven.apache.org/POM/4.0.0:Dependency interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Dependency documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomDependency extends MavenDomElement, MavenDomArtifactCoordinates {
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getVersion();

  /**
   * Returns the value of the type child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:type documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the type child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenPackagingTypeConverter.class)
  GenericDomValue<String> getType();

  /**
   * Returns the value of the classifier child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:classifier documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the classifier child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getClassifier();

  /**
   * Returns the value of the scope child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:scope documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the scope child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenDependencyScopeConverter.class)
  GenericDomValue<String> getScope();

  /**
   * Returns the value of the systemPath child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:systemPath documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the systemPath child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenDependencySystemPathConverter.class)
  GenericDomValue<PsiFile> getSystemPath();

  /**
   * Returns the value of the exclusions child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:exclusions documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the exclusions child.
   */
  @NotNull
  MavenDomExclusions getExclusions();

  /**
   * Returns the value of the optional child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:optional documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the optional child.
   */
  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<Boolean> getOptional();
}
