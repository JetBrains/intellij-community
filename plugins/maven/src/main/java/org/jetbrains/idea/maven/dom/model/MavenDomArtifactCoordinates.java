package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesVersionConverter;

public interface MavenDomArtifactCoordinates extends MavenDomShortArtifactCoordinates {
  @Required
  @Convert(MavenArtifactCoordinatesVersionConverter.class)
  GenericDomValue<String> getVersion();
}
