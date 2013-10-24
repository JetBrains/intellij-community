package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;

/**
 * @author Sergey Evdokimov
 */
public class MavenArtifactCoordinatesPresentationProvider extends PresentationProvider<MavenDomArtifactCoordinates> {

  private static final String UNKNOWN = "<unknown>";

  @Nullable
  @Override
  public String getName(MavenDomArtifactCoordinates coordinates) {
    return StringUtil.notNullize(coordinates.getGroupId().getStringValue(), UNKNOWN)
      + ':'
      + StringUtil.notNullize(coordinates.getArtifactId().getStringValue(), UNKNOWN)
      + ':'
      + StringUtil.notNullize(coordinates.getVersion().getStringValue(), UNKNOWN);
  }
}
