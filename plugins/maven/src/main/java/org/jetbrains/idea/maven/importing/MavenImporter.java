/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.project.ResolveContext;
import org.jetbrains.idea.maven.project.SupportedRequestType;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public abstract class MavenImporter {
  public static final ExtensionPointName<MavenImporter> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.importer");
  protected final String myPluginGroupID;
  protected final String myPluginArtifactID;

  public MavenImporter(@NonNls String pluginGroupID, @NonNls String pluginArtifactID) {
    myPluginGroupID = pluginGroupID;
    myPluginArtifactID = pluginArtifactID;
  }

  public static List<MavenImporter> getSuitableImporters(MavenProject p) {
    List<MavenImporter> result = null;
    Set<ModuleType> moduleTypes = null;

    for (MavenImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (importer.isApplicable(p)) {
        if (result == null) {
          result = new ArrayList<>();
          moduleTypes = new THashSet<>();
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
    ModuleType moduleType = result.get(0).getModuleType();
    List<String> supportedPackagings = new ArrayList<>();
    for (MavenImporter importer : result) {
      supportedPackagings.clear();
      importer.getSupportedPackagings(supportedPackagings);
      if (supportedPackagings.contains(p.getPackaging())) {
        moduleType = importer.getModuleType();
        break;
      }
    }

    final ModuleType finalModuleType = moduleType;
    return ContainerUtil.filter(result, importer -> importer.getModuleType() == finalModuleType);
  }

  public boolean isApplicable(MavenProject mavenProject) {
    return mavenProject.findPlugin(myPluginGroupID, myPluginArtifactID) != null;
  }

  @NotNull
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public void getSupportedPackagings(Collection<String> result) {
  }

  public void getSupportedDependencyTypes(Collection<String> result, SupportedRequestType type) {
  }

  public void getSupportedDependencyScopes(Collection<String> result) {
  }

  @Nullable
  public Pair<String, String> getExtraArtifactClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type) {
    return null;
  }

  /**
   * @deprecated use {@link #resolve(Project, MavenProject, NativeMavenProjectHolder, MavenEmbedderWrapper, ResolveContext)}
   */
  @Deprecated
  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder)
    throws MavenProcessCanceledException {
  }

  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context)
    throws MavenProcessCanceledException {
    resolve(project, mavenProject, nativeMavenProject, embedder);
  }

  public abstract void preProcess(Module module,
                                  MavenProject mavenProject,
                                  MavenProjectChanges changes,
                                  IdeModifiableModelsProvider modifiableModelsProvider);

  public abstract void process(IdeModifiableModelsProvider modifiableModelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               MavenProjectsTree mavenModel,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks);

  public void postProcess(Module module,
                          MavenProject mavenProject,
                          MavenProjectChanges changes,
                          IdeModifiableModelsProvider modifiableModelsProvider) {
  }

  public boolean processChangedModulesOnly() {
    return true;
  }

  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    List<String> sources = new ArrayList<>();
    collectSourceFolders(mavenProject, sources);
    for (String path : sources) {
      result.consume(path, JavaSourceRootType.SOURCE);
    }
    List<String> testSources = new ArrayList<>();
    collectTestFolders(mavenProject, testSources);
    for (String path : testSources) {
      result.consume(path, JavaSourceRootType.TEST_SOURCE);
    }
  }

  /**
   * @deprecated override {@link #collectSourceRoots} instead
   */
  @Deprecated
  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
  }

  /**
   * @deprecated override {@link #collectSourceRoots} instead
   */
  @Deprecated
  public void collectTestFolders(MavenProject mavenProject, List<String> result) {
  }

  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
  }

  @Nullable
  protected Element getConfig(MavenProject p) {
    return p.getPluginConfiguration(myPluginGroupID, myPluginArtifactID);
  }

  @Nullable
  protected Element getConfig(MavenProject p, @NonNls String path) {
    return MavenJDOMUtil.findChildByPath(getConfig(p), path);
  }

  @Nullable
  protected String findConfigValue(MavenProject p, @NonNls String path) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path);
  }

  @Nullable
  protected String findConfigValue(MavenProject p, @NonNls String path, @NonNls String defaultValue) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path, defaultValue);
  }

  @Nullable
  protected Element getGoalConfig(MavenProject p, @NonNls String goal) {
    return p.getPluginGoalConfiguration(myPluginGroupID, myPluginArtifactID, goal);
  }

  @Nullable
  protected String findGoalConfigValue(MavenProject p, @NonNls String goal, @NonNls String path) {
    return MavenJDOMUtil.findChildValueByPath(getGoalConfig(p, goal), path);
  }

  /**
   * Override this method if you'd like control over properties used by Maven, e.g. for pom interpolation.
   */
  public void customizeUserProperties(Project project, MavenProject mavenProject, Properties properties) {
  }
}
