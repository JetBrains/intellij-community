package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;

/**
 * @author Sergey Evdokimov
 */
public class MavenDomPluginPresentationProvider extends PresentationProvider<MavenDomPlugin> {

  @Nullable
  @Override
  public String getName(MavenDomPlugin plugin) {
    String artifactId = plugin.getArtifactId().getStringValue();
    String version = plugin.getVersion().getStringValue();

    return version == null ? artifactId : artifactId + ':' + version;
  }
}
