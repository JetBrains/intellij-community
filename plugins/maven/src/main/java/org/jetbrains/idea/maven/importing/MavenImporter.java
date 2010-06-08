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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MavenImporter {
  public static ExtensionPointName<MavenImporter> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.importer");
  protected final String myPluginGroupID;
  protected final String myPluginArtifactID;

  public MavenImporter(String pluginGroupID, String pluginArtifactID) {
    myPluginGroupID = pluginGroupID;
    myPluginArtifactID = pluginArtifactID;
  }

  public static List<MavenImporter> getSuitableImporters(MavenProject p) {
    final List<MavenImporter> result = new ArrayList<MavenImporter>();
    for (MavenImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (importer.isApplicable(p)) {
        result.add(importer);
      }
    }
    return result;
  }

  public boolean isApplicable(MavenProject mavenProject) {
    return mavenProject.findPlugin(myPluginGroupID, myPluginArtifactID) != null;
  }

  public abstract boolean isSupportedDependency(MavenArtifact artifact);

  @Nullable
  public Pair<String, String> getExtraArtifactClassifierAndExtension(MavenArtifact artifact, MavenExtraArtifactType type) {
    return null;
  }

  public void resolve(MavenProject project, NativeMavenProjectHolder nativeMavenProject, MavenEmbedderWrapper embedder)
    throws MavenProcessCanceledException {
  }

  public abstract void preProcess(Module module,
                                  MavenProject mavenProject,
                                  MavenProjectChanges changes,
                                  MavenModifiableModelsProvider modifiableModelsProvider);

  public abstract void process(MavenModifiableModelsProvider modifiableModelsProvider,
                               Module module,
                               MavenRootModelAdapter rootModel,
                               MavenProjectsTree mavenModel,
                               MavenProject mavenProject,
                               MavenProjectChanges changes,
                               Map<MavenProject, String> mavenProjectToModuleName,
                               List<MavenProjectsProcessorTask> postTasks);

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
  }

  public void collectTestFolders(MavenProject mavenProject, List<String> result) {
  }

  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
  }

  @Nullable
  protected Element getConfig(MavenProject p) {
    return p.getPluginConfiguration(myPluginGroupID, myPluginArtifactID);
  }

  @Nullable
  protected Element getConfig(MavenProject p, String path) {
    return MavenJDOMUtil.findChildByPath(getConfig(p), path);
  }

  @Nullable
  protected String findConfigValue(MavenProject p, String path) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path);
  }

  @Nullable
  protected String findConfigValue(MavenProject p, String path, String defaultValue) {
    return MavenJDOMUtil.findChildValueByPath(getConfig(p), path, defaultValue);
  }

  @Nullable
  protected Element getGoalConfig(MavenProject p, String goal) {
    return p.getPluginGoalConfiguration(myPluginGroupID, myPluginArtifactID, goal);
  }

  @Nullable
  protected String findGoalConfigValue(MavenProject p, String goal, String path) {
    return MavenJDOMUtil.findChildValueByPath(getGoalConfig(p, goal), path);
  }
}
