// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Extension interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Extension documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomExtension extends MavenDomElement, MavenDomArtifactCoordinates {
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getGroupId();

  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getVersion();
}
