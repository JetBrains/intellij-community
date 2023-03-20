package org.jetbrains.idea.maven.importing;

import com.intellij.util.PairConsumer;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.plugins.groovy.GroovyPluginConfigurator;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;

/**
 * This class can not be moved to org.jetbrains.idea.maven.plugins.groovy package because it's used from 'Eclipse Groovy Compiler Plugin'
 */
public abstract class GroovyImporter extends MavenImporter {
  public GroovyImporter(String pluginGroupID, String pluginArtifactID) {
    super(pluginGroupID, pluginArtifactID);
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    MavenPlugin plugin = mavenProject.findPlugin(myPluginGroupID, myPluginArtifactID);
    if (plugin != null) {
      GroovyPluginConfigurator.Companion.collectGroovyFolders(plugin, true).forEach(path -> {
        result.consume(path, JavaSourceRootType.SOURCE);
      });
      GroovyPluginConfigurator.Companion.collectGroovyFolders(plugin, false).forEach(path -> {
        result.consume(path, JavaSourceRootType.TEST_SOURCE);
      });
    }
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    MavenPlugin plugin = mavenProject.findPlugin(myPluginGroupID, myPluginArtifactID);
    if (plugin != null) {
      result.addAll(GroovyPluginConfigurator.Companion.collectIgnoredFolders(mavenProject, plugin));
    }
  }
}
