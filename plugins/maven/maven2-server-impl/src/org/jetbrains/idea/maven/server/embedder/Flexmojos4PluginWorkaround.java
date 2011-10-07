package org.jetbrains.idea.maven.server.embedder;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.DefaultPluginManager;
import org.apache.maven.plugin.InvalidPluginException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.version.PluginVersionNotFoundException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

// IDEA-73842
public class Flexmojos4PluginWorkaround extends DefaultPluginManager {
  @Override
  public PluginDescriptor verifyPlugin(Plugin plugin, MavenProject project, Settings settings, ArtifactRepository localRepository) throws
                                                                                                                                   ArtifactResolutionException,
                                                                                                                                   PluginVersionResolutionException,
                                                                                                                                   ArtifactNotFoundException,
                                                                                                                                   InvalidVersionSpecificationException,
                                                                                                                                   InvalidPluginException,
                                                                                                                                   PluginManagerException,
                                                                                                                                   PluginNotFoundException,
                                                                                                                                   PluginVersionNotFoundException {
    if ("flexmojos-maven-plugin".equals(plugin.getArtifactId()) && "org.sonatype.flexmojos".equals(plugin.getGroupId())) {
      String pluginVersion = plugin.getVersion();
      if (pluginVersion == null) {
        pluginVersion = pluginVersionManager.resolvePluginVersion(plugin.getGroupId(), plugin.getArtifactId(),
                                                                  project, settings, localRepository);
        plugin.setVersion(pluginVersion);
      }

      if (StringUtil.compareVersionNumbers(pluginVersion, "4") >= 0) {
        //noinspection ConstantConditions
        return null;
      }
    }

    return super.verifyPlugin(plugin, project, settings, localRepository);
  }
}