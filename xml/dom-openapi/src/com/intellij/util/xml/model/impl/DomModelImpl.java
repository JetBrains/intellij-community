// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.model.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.model.DomModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class DomModelImpl<T extends DomElement> implements DomModel<T> {

  protected final DomFileElement<T> myMergedModel;
  protected final Set<XmlFile> myConfigFiles;
  private Class<T> myClass;
  private Project myProject;

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

  @Override
  public @NotNull T getMergedModel() {
    if (myMergedModel == null) {
      final DomManager domManager = DomManager.getDomManager(myProject);
      return domManager.createModelMerger().mergeModels(myClass, ContainerUtil.mapNotNull(myConfigFiles,
                                                                                          (NullableFunction<XmlFile, T>)xmlFile -> {
                                                                                            DomFileElement<T> fileElement = domManager.getFileElement(xmlFile, myClass);
                                                                                            return fileElement == null ? null : fileElement.getRootElement();
                                                                                          }));
    }
    return myMergedModel.getRootElement();
  }

  @Override
  public @NotNull Set<XmlFile> getConfigFiles() {
    return myConfigFiles;
  }

  @Override
  public @Unmodifiable @NotNull List<DomFileElement<T>> getRoots() {
    if (myMergedModel == null) {
      return ContainerUtil.mapNotNull(myConfigFiles, (NullableFunction<XmlFile, DomFileElement<T>>)xmlFile -> DomManager.getDomManager(xmlFile.getProject()).getFileElement(xmlFile, myClass));
    }
    return myMergedModel instanceof MergedObject ? ((MergedObject) myMergedModel).getImplementations() : Collections.singletonList(myMergedModel);
  }

  public @NotNull Project getProject() {
    return myProject;
  }
}
