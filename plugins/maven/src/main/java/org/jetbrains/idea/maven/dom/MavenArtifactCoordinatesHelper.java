package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomShortMavenArtifactCoordinates;
import org.jetbrains.idea.maven.project.MavenId;

public class MavenArtifactCoordinatesHelper {
  public static MavenId getId(ConvertContext context) {
    MavenDomShortMavenArtifactCoordinates coords = (MavenDomShortMavenArtifactCoordinates)context.getInvocationElement().getParent();
    String version = "";
    if (coords instanceof MavenDomArtifactCoordinates) {
      version = resolveProperties(((MavenDomArtifactCoordinates)coords).getVersion());
    }
    MavenId result = new MavenId(resolveProperties(coords.getGroupId()),
                                 resolveProperties(coords.getArtifactId()),
                                 version);
    return result;
  }

  private static String resolveProperties(GenericDomValue<String> value) {
    String result = PropertyResolver.resolve(value);
    return result == null ? "" : result;
  }
}
