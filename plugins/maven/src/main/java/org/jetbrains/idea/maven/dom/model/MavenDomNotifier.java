// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

/**
 * http://maven.apache.org/POM/4.0.0:Notifier interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:Notifier documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomNotifier extends MavenDomElement {

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
  GenericDomValue<String> getType();

  /**
   * Returns the value of the sendOnError child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sendOnError documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the sendOnError child.
   */
  @NotNull
  GenericDomValue<Boolean> getSendOnError();

  /**
   * Returns the value of the sendOnFailure child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sendOnFailure documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the sendOnFailure child.
   */
  @NotNull
  GenericDomValue<Boolean> getSendOnFailure();

  /**
   * Returns the value of the sendOnSuccess child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sendOnSuccess documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the sendOnSuccess child.
   */
  @NotNull
  GenericDomValue<Boolean> getSendOnSuccess();

  /**
   * Returns the value of the sendOnWarning child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:sendOnWarning documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the sendOnWarning child.
   */
  @NotNull
  GenericDomValue<Boolean> getSendOnWarning();

  /**
   * Returns the value of the address child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:address documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the address child.
   */
  @NotNull
  GenericDomValue<String> getAddress();

  /**
   * Returns the value of the configuration child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:configuration documentation</h3>
   * 0.0.0+
   * </pre>
   *
   * @return the value of the configuration child.
   */
  @NotNull
  MavenDomElement getConfiguration();
}
