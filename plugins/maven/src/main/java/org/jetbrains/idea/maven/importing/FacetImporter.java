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

import com.intellij.facet.*;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class FacetImporter<FACET_TYPE extends Facet, FACET_CONFIG_TYPE extends FacetConfiguration, FACET_TYPE_TYPE extends FacetType<FACET_TYPE, FACET_CONFIG_TYPE>>
  extends MavenImporter {
  protected final FACET_TYPE_TYPE myFacetType;
  protected final String myDefaultFacetName;

  public FacetImporter(String pluginGroupID,
                       String pluginArtifactID,
                       FACET_TYPE_TYPE type,
                       String defaultFacetName) {
    super(pluginGroupID, pluginArtifactID);
    myFacetType = type;
    myDefaultFacetName = defaultFacetName;
  }

  public FACET_TYPE_TYPE getFacetType() {
    return myFacetType;
  }

  public String getDefaultFacetName() {
    return myDefaultFacetName;
  }

  @Override
  public boolean isSupportedDependency(MavenArtifact artifact) {
    return false;
  }

  @Override
  public void preProcess(Module module,
                         MavenProject mavenProject,
                         MavenProjectChanges changes,
                         MavenModifiableModelsProvider modifiableModelsProvider) {
    prepareImporter(mavenProject);
    disableFacetAutodetection(module);
    ensureFacetExists(module, mavenProject, modifiableModelsProvider);
  }

  private void ensureFacetExists(Module module, MavenProject mavenProject, MavenModifiableModelsProvider modifiableModelsProvider) {
    ModifiableFacetModel model = modifiableModelsProvider.getFacetModel(module);

    FACET_TYPE f = findFacet(model);
    if (f != null) return;

    f = myFacetType.createFacet(module, myDefaultFacetName, myFacetType.createDefaultConfiguration(), null);
    model.addFacet(f);
    setupFacet(f, mavenProject);
  }

  protected void prepareImporter(MavenProject p) {
  }

  private void disableFacetAutodetection(Module module) {
    FacetAutodetectingManager m = FacetAutodetectingManager.getInstance(module.getProject());
    m.disableAutodetectionInModule(myFacetType, module);
  }

  protected abstract void setupFacet(FACET_TYPE f, MavenProject mavenProject);

  @Override
  public void process(MavenModifiableModelsProvider modifiableModelsProvider,
                      Module module,
                      MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel,
                      MavenProject mavenProject,
                      MavenProjectChanges changes,
                      Map<MavenProject, String> mavenProjectToModuleName,
                      List<MavenProjectsProcessorTask> postTasks) {
    FACET_TYPE f = findFacet(modifiableModelsProvider.getFacetModel(module));
    if (f == null) return; // facet may has been removed between preProcess and process calls

    reimportFacet(modifiableModelsProvider, module, rootModel, f, mavenModel, mavenProject, changes, mavenProjectToModuleName, postTasks);
  }

  private FACET_TYPE findFacet(FacetModel model) {
    return findFacet(model, myFacetType, myDefaultFacetName);
  }

  protected <T extends Facet > T findFacet(FacetModel model, FacetType<T, ?> type, String defaultFacetName) {
    T result = model.findFacet(type.getId(), defaultFacetName);
    if (result == null) result = model.getFacetByType(type.getId());
    return result;
  }

  protected abstract void reimportFacet(MavenModifiableModelsProvider modelsProvider,
                                        Module module,
                                        MavenRootModelAdapter rootModel,
                                        FACET_TYPE facet,
                                        MavenProjectsTree mavenTree,
                                        MavenProject mavenProject,
                                        MavenProjectChanges changes,
                                        Map<MavenProject, String> mavenProjectToModuleName,
                                        List<MavenProjectsProcessorTask> postTasks);

  protected String getTargetName(MavenProject p) {
    return p.getFinalName();
  }

  protected String getTargetFileName(MavenProject p) {
    return getTargetFileName(p, "." + getTargetExtension(p));
  }

  protected String getTargetFileName(MavenProject p, String suffix) {
    return getTargetName(p) + suffix;
  }

  protected String getTargetFilePath(MavenProject p, String suffix) {
    return makePath(p, p.getBuildDirectory(), getTargetName(p) + suffix);
  }

  protected String getTargetOutputPath(MavenProject p, String... subFoldersAndFile) {
    List<String> elements = new ArrayList<String>();
    elements.add(p.getBuildDirectory());
    Collections.addAll(elements, subFoldersAndFile);
    return makePath(p, ArrayUtil.toStringArray(elements));
  }

  protected String makePath(MavenProject p, String... elements) {
    StringBuilder tailBuff = new StringBuilder();
    for (String e : elements) {
      if (tailBuff.length() > 0) tailBuff.append("/");
      tailBuff.append(e);
    }
    String tail = tailBuff.toString();
    String result = FileUtil.isAbsolute(tail) ? tail : new File(p.getDirectory(), tail).getPath();

    return FileUtil.toSystemIndependentName(PathUtil.getCanonicalPath(result));
  }

  protected String getTargetExtension(MavenProject p) {
    return p.getPackaging();
  }
}
