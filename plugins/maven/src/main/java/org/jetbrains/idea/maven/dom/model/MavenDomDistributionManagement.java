// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.MavenDistributionStatusConverter;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

public interface MavenDomDistributionManagement extends MavenDomElement {
  @NotNull
  MavenDomDeploymentRepository getRepository();

  @NotNull
  MavenDomDeploymentRepository getSnapshotRepository();

  @NotNull
  MavenDomSite getSite();

  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getDownloadUrl();

  @NotNull
  MavenDomRelocation getRelocation();

  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenDistributionStatusConverter.class)
  GenericDomValue<String> getStatus();
}
