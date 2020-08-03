/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.model.DomModel;
import com.intellij.util.xml.model.MultipleDomModelFactory;
import com.intellij.util.xml.model.SimpleModelFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BaseDomModelFactory<S extends UserDataHolder, T extends DomElement, M extends DomModel<T>, C extends PsiElement>
    extends DomModelFactoryHelper<T> implements SimpleModelFactory<T, M>, MultipleDomModelFactory<S, T, M> {

  private final Project myProject;
  protected BaseDomModelFactory(@NotNull Class<T> aClass, final Project project, @NonNls String name) {
    super(aClass, DomService.getInstance().createModelMerger());

    myProject = project;
  }

  @Nullable
  public M getModel(@NotNull C context){
    final PsiFile psiFile = context.getContainingFile();
    if (psiFile instanceof XmlFile) {
      return getModelByConfigFile((XmlFile)psiFile);
    }
    return null;
  }

  @Nullable
  protected M computeModel(@NotNull XmlFile psiFile, @Nullable S scope) {
    if (scope == null) {
      return null;
    }
    final List<M> models = getAllModels(scope);
    for (M model : models) {
      final Set<XmlFile> configFiles = model.getConfigFiles();
      if (configFiles.contains(psiFile)) {
        return model;
      }
    }
    return null;
  }


  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Override
  public M getModelByConfigFile(@Nullable XmlFile file) {
    if (file == null) return null;
    final XmlFile originalFile = (XmlFile)file.getOriginalFile();
    final S scope = getModelScope(originalFile);
    return computeModel(originalFile, scope);
  }

  @Override
  public @Nullable DomFileElement<T> createMergedModelRoot(Set<? extends XmlFile> configFiles) {
    List<DomFileElement<T>> configs = new ArrayList<>(configFiles.size());
    for (XmlFile configFile : configFiles) {
      ContainerUtil.addIfNotNull(configs, getDomRoot(configFile));
    }
    return configs.isEmpty() ? null : getModelMerger().mergeModels(DomFileElement.class, configs);
  }

  protected abstract S getModelScope(final XmlFile file);

  @Nullable
  protected abstract List<M> computeAllModels(@NotNull S scope);

  protected abstract M createCombinedModel(Set<XmlFile> configFiles, DomFileElement<T> mergedModel, M firstModel, final S scope);

  //
  @Override
  public @NotNull List<M> getAllModels(@NotNull S s) {
    final List<M> models = computeAllModels(s);
    return models == null ? Collections.emptyList() : models;
  }

  @Override
  public Set<XmlFile> getAllConfigFiles(@NotNull S scope) {
    final HashSet<XmlFile> xmlFiles = new HashSet<>();
    for (M model: getAllModels(scope)) {
      xmlFiles.addAll(model.getConfigFiles());
    }
    return xmlFiles;
  }

  @Nullable
  @Override
  public M getCombinedModel(@Nullable S s) {
    if (s == null) return null;
    final List<M> models = getAllModels(s);
    switch (models.size()) {
      case 0:
        return null;
      case 1:
        return models.get(0);
    }
    final Set<XmlFile> configFiles = new LinkedHashSet<>();
    final LinkedHashSet<DomFileElement<T>> list = new LinkedHashSet<>(models.size());
    for (M model: models) {
      final Set<XmlFile> files = model.getConfigFiles();
      for (XmlFile file: files) {
        ContainerUtil.addIfNotNull(list, getDomRoot(file));
      }
      configFiles.addAll(files);
    }
    final DomFileElement<T> mergedModel = getModelMerger().mergeModels(DomFileElement.class, list);
    final M firstModel = models.get(0);
    return createCombinedModel(configFiles, mergedModel, firstModel, s);
  }
}