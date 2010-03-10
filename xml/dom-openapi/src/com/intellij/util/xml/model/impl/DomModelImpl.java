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

package com.intellij.util.xml.model.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class DomModelImpl<T extends DomElement> {

  protected final DomFileElement<T> myMergedModel;
  protected final Set<XmlFile> myConfigFiles;
  private Class<T> myClass;
  private Project myProject;

  /**
   * Using this method may result in a large memory usage, since it will keep all the DOM and PSI for all the config files
   * @return
   */
  @Deprecated
  public DomModelImpl(T mergedModel, @NotNull Set<XmlFile> configFiles) {
    myMergedModel = DomUtil.getFileElement(mergedModel);
    myConfigFiles = configFiles;
  }

  public DomModelImpl(DomFileElement<T> mergedModel, @NotNull Set<XmlFile> configFiles) {
    myMergedModel = DomUtil.getFileElement(mergedModel);
    myConfigFiles = configFiles;
  }

  public DomModelImpl(@NotNull Set<XmlFile> configFiles, Class<T> clazz, Project project) {
    myProject = project;
    myMergedModel = null;
    myConfigFiles = configFiles;
    myClass = clazz;
  }

  @NotNull
  public T getMergedModel() {
    if (myMergedModel == null) {
      final DomManager domManager = DomManager.getDomManager(myProject);
      return domManager.createModelMerger().mergeModels(myClass, ContainerUtil.mapNotNull(myConfigFiles, new NullableFunction<XmlFile, T>() {
        public T fun(XmlFile xmlFile) {
          DomFileElement<T> fileElement = domManager.getFileElement(xmlFile, myClass);
          return fileElement == null ? null : fileElement.getRootElement();
        }
      }));
    }
    return myMergedModel.getRootElement();
  }

  @NotNull
  public Set<XmlFile> getConfigFiles() {
    return myConfigFiles;
  }

  @NotNull
  public List<DomFileElement<T>> getRoots() {
    if (myMergedModel == null) {
      return ContainerUtil.mapNotNull(myConfigFiles, new NullableFunction<XmlFile, DomFileElement<T>>() {
        public DomFileElement<T> fun(XmlFile xmlFile) {
          return DomManager.getDomManager(xmlFile.getProject()).getFileElement(xmlFile, myClass);
        }
      });
    }
    return myMergedModel instanceof MergedObject ? ((MergedObject) myMergedModel).getImplementations() : Collections.singletonList(myMergedModel);
  }

}
