// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Prerequisites interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Prerequisites documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomPrerequisites extends MavenDomElement {

  /**
   * Returns the value of the maven child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:maven documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the maven child.
   */
  @NotNull
  GenericDomValue<String> getMaven();
}
