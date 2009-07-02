package org.jetbrains.idea.maven.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.project.MavenId;

public class MavenArtifactCoordinatesHelper {
  public static MavenId getId(ConvertContext context) {
    MavenDomShortArtifactCoordinates coords = (MavenDomShortArtifactCoordinates)context.getInvocationElement().getParent();
    return getId(coords);
  }

  public static MavenId getId(MavenDomShortArtifactCoordinates coords) {
    String version = "";
    if (coords instanceof MavenDomArtifactCoordinates) {
      version = resolveProperties(((MavenDomArtifactCoordinates)coords).getVersion());
    }
    return new MavenId(resolveProperties(coords.getGroupId()),
                       resolveProperties(coords.getArtifactId()),
                       version);
  }

  private static String resolveProperties(GenericDomValue<String> value) {
    String result = MavenPropertyResolver.resolve(value);
    return result == null ? "" : result;
  }
}
