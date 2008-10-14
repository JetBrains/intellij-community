package org.jetbrains.idea.maven.dom;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.idea.maven.dom.model.MavenArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.ShortMavenArtifactCoordinates;
import org.jetbrains.idea.maven.utils.MavenId;

public class MavenArtifactCoordinatesHelper {
  public static MavenId getId(ConvertContext context) {
    ShortMavenArtifactCoordinates coords = (ShortMavenArtifactCoordinates)context.getInvocationElement().getParent();
    MavenId result = new MavenId(resolveProperties(coords.getGroupId()),
                                 resolveProperties(coords.getArtifactId()),
                                 "");
    if (coords instanceof MavenArtifactCoordinates) {
      result.version = resolveProperties(((MavenArtifactCoordinates)coords).getVersion());
    }
    return result;
  }

  private static String resolveProperties(GenericDomValue<String> value) {
    String result = PropertyResolver.resolve(value);
    return result == null ? "" : result;
  }
}
