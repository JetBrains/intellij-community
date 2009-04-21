// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;

public interface MavenDomProfile extends MavenDomElement, MavenDomProjectModelBase {
  @NotNull
  @Required
  GenericDomValue<String> getId();

  @NotNull
  MavenDomActivation getActivation();
}
