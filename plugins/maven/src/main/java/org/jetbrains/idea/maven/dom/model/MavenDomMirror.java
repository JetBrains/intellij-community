package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.repositories.MavenRepositoryConverter;

public interface MavenDomMirror extends MavenDomElement {
  @NotNull
  @Convert(MavenRepositoryConverter.Url.class)
  GenericDomValue<String> getUrl();

  @NotNull
  @Required(value = false, nonEmpty = true)
  GenericDomValue<String> getId();
}
