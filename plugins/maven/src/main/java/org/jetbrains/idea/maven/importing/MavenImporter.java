// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.*;

/**
 * Extension point for customization maven module import process.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public abstract class MavenImporter {
  public static final ExtensionPointName<MavenImporter> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.importer");

  protected final String myPluginGroupID;
  protected final String myPluginArtifactID;

  public MavenImporter(@NonNls String pluginGroupID, @NonNls String pluginArtifactID) {
    myPluginGroupID = pluginGroupID;
    myPluginArtifactID = pluginArtifactID;
  }

  public static List<MavenImporter> getSuitableImporters(MavenProject p) {
    return getSuitableImporters(p, false);
  }

  public static List<MavenImporter> getSuitableImporters(MavenProject p, boolean isWorkspaceImport) {
    List<MavenImporter> result = null;
    Set<ModuleType<?>> moduleTypes = null;

    for (MavenImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (isWorkspaceImport && importer.isMigratedToConfigurator()) continue;

      if (importer.isApplicable(p)) {
        if (result == null) {
          result = new ArrayList<>();
          moduleTypes = new HashSet<>();
        }

        result.add(importer);
        moduleTypes.add(importer.getModuleType());
      }
    }

    if (result == null) {
      return Collections.emptyList();
    }

    if (moduleTypes.size() <= 1) {
      return result;
    }

    // This code is reached when several importers say that they are applicable but they want to have different module types.
    // Now we select one module type and return only those importers that are ok with it.
    // If possible - return at least one importer that explicitly supports packaging of the given maven project.
    ModuleType<?> moduleType = result.get(0).getModuleType();
    List<String> supportedPackagingTypes = new ArrayList<>();
    for (MavenImporter importer : result) {
      supportedPackagingTypes.clear();
      importer.getSupportedPackagings(supportedPackagingTypes);
      if (supportedPackagingTypes.contains(p.getPackaging())) {
        moduleType = importer.getModuleType();
        break;
      }
    }

    final ModuleType<?> finalModuleType = moduleType;
    return ContainerUtil.filter(result, importer -> importer.getModuleType() == finalModuleType);
  }

  public boolean isApplicable(MavenProject mavenProject) {
    return mavenProject.findPlugin(myPluginGroupID, myPluginArtifactID) != null;
  }

  /**
   * @deprecated use facets instead of module types
   */
  @Deprecated
  public @NotNull ModuleType<? extends ModuleBuilder> getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void getSupportedPackagings(Collection<String> result) { }

  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) { }

  /**
   * @deprecated this API is not supported anymore, and there is no replacement
   */
  @Deprecated
  public void getSupportedDependencyScopes(Collection<String> result) { }

  /**
   * @deprecated this API is not supported anymore, and there is no replacement
   */
  @Deprecated
  public @Nullable Pair<String, String> getExtraArtifactClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type) {
    return null;
  }

  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context)
    throws MavenProcessCanceledException {
  }

  /**
   * This is 'work in progress' API and must not be used directly until further notice
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public boolean isMigratedToConfigurator() { return false; }

  /**
   * Import pre process callback.
   */
  public void preProcess(Module module,
                         MavenProject mavenProject,
                         MavenProjectChanges changes,
                         IdeModifiableModelsProvider modifiableModelsProvider) { }

  /**
   * Import process callback.
   *
   * @param postTasks is deprecated, use {@link org.jetbrains.idea.maven.project.MavenImportListener} instead
   */
  public void process(IdeModifiableModelsProvider modifiableModelsProvider,
                      Module module,
                      MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel,
                      MavenProject mavenProject,
                      MavenProjectChanges changes,
                      Map<MavenProject, String> mavenProjectToModuleName,
                      @Deprecated // use {@link org.jetbrains.idea.maven.project.MavenImportListener} instead
                      List<MavenProjectsProcessorTask> postTasks) { }

  /**
   * Import post process callback.
   */
  public void postProcess(Module module,
                          MavenProject mavenProject,
                          MavenProjectChanges changes,
                          IdeModifiableModelsProvider modifiableModelsProvider) {
  }

  public boolean processChangedModulesOnly() {
    return true;
  }

  @SuppressWarnings("BoundedWildcard")
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
  }

  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) { }

  protected @Nullable Element getConfig(MavenProject p) {
    return p.getPluginConfiguration(myPluginGroupID, myPluginArtifactID);
  }

  protected @Nullable Element getConfig(MavenProject p, @NonNls String path) {
    return MavenJDOMUtil.findChildByPath(getConfig(p), path);
  }

  protected @Nullable String findConfigValue(MavenProject p, @NonNls String path) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path);
  }

  protected @Nullable String findConfigValue(MavenProject p, @NonNls String path, @NonNls String defaultValue) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path, defaultValue);
  }

  protected @Nullable Element getGoalConfig(MavenProject p, @NonNls String goal) {
    return p.getPluginGoalConfiguration(myPluginGroupID, myPluginArtifactID, goal);
  }

  protected @Nullable String findGoalConfigValue(MavenProject p, @NonNls String goal, @NonNls String path) {
    return MavenJDOMUtil.findChildValueByPath(getGoalConfig(p, goal), path);
  }

  /**
   * Override this method if you'd like control over properties used by Maven, e.g. for pom interpolation.
   */
  public void customizeUserProperties(Project project, MavenProject mavenProject, Properties properties) { }
}
