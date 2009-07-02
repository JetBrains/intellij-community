// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:IssueManagement interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:IssueManagement documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomIssueManagement extends MavenDomElement {
  /**
   * Returns the value of the system child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:system documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the system child.
   */
  @NotNull
  GenericDomValue<String> getSystem();

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
