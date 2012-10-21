/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.jps.maven.model.impl.MavenModuleExtensionProperties;
import org.jetbrains.jps.maven.model.impl.ResourceProperties;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/19/12
 */
public class MavenFacetConfiguration implements FacetConfiguration, PersistentStateComponent<MavenModuleExtensionProperties> {
  private static final FacetEditorTab[] TABS_EMPTY_ARRAY = new FacetEditorTab[0];

  private final MavenModuleExtensionProperties myState = new MavenModuleExtensionProperties();

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return TABS_EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public MavenModuleExtensionProperties getState() {
    return myState;
  }

  @Override
  public void loadState(MavenModuleExtensionProperties state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void clearState() {
    myState.myResources.clear();
  }

  public void addResources(Collection<MavenResource> resources) {
    for (MavenResource resource : resources) {
      final ResourceProperties props = new ResourceProperties();
      final String dir = resource.getDirectory();
      props.directory = dir != null? FileUtil.toSystemIndependentName(dir) : null;

      final String target = resource.getTargetPath();
      props.targetPath = target != null? FileUtil.toSystemIndependentName(target) : null;

      props.isFiltered = resource.isFiltered();
      props.includes.clear();
      for (String include : resource.getIncludes()) {
        props.includes.add(FileUtil.convertAntToRegexp(include.trim()));
      }
      props.excludes.clear();
      for (String exclude : resource.getExcludes()) {
        props.excludes.add(FileUtil.convertAntToRegexp(exclude.trim()));
      }
      myState.myResources.add(props);
    }
  }

  @Nullable
  public static MavenFacetConfiguration getInstance(Module module) {
    final MavenFacet facet = MavenFacet.getInstance(module);
    return facet != null? facet.getConfiguration() : null;
  }

}
