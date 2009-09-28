// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenRepositoryLayoutConverter;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

public interface MavenDomRepositoryBase extends MavenDomElement {
  @NotNull
  @Required
  GenericDomValue<String> getId();

  @NotNull
  GenericDomValue<String> getName();

  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUrl();

  @NotNull
  @Required(value = false, nonEmpty = true)
  @Convert(MavenRepositoryLayoutConverter.class)
  GenericDomValue<String> getLayout();
}
