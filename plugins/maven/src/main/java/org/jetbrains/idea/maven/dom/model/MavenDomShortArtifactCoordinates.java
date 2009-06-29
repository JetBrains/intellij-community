package org.jetbrains.idea.maven.dom.model;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.Required;
import org.jetbrains.idea.maven.dom.MavenArtifactCoordinatesArtifactIdConverter;
import org.jetbrains.idea.maven.dom.MavenArtifactCoordinatesGroupIdConverter;

public interface MavenDomShortArtifactCoordinates {
  @Required
  @Convert(MavenArtifactCoordinatesGroupIdConverter.class)
  GenericDomValue<String> getGroupId();

  @Required
  @Convert(MavenArtifactCoordinatesArtifactIdConverter.class)
  GenericDomValue<String> getArtifactId();
}
