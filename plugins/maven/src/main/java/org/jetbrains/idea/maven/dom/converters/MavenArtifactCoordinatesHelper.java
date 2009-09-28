package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.project.MavenId;

public class MavenArtifactCoordinatesHelper {
  public static MavenId getId(ConvertContext context) {
    MavenDomShortArtifactCoordinates coords = (MavenDomShortArtifactCoordinates)context.getInvocationElement().getParent();
    return getId(coords);
  }

  public static MavenId getId(MavenDomShortArtifactCoordinates coords) {
    String version = "";
    if (coords instanceof MavenDomArtifactCoordinates) {
      version = ((MavenDomArtifactCoordinates)coords).getVersion().getStringValue();
    }
    return new MavenId(coords.getGroupId().getStringValue(),
                       coords.getArtifactId().getStringValue(),
                       version);
  }
}
