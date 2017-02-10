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
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.model.DomModel;
import com.intellij.util.xml.model.DomModelCache;
import com.intellij.util.xml.model.MultipleDomModelFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: Sergey.Vasiliev
 */
public abstract class CachedMultipleDomModelFactory<Scope extends UserDataHolder, T extends DomElement, M extends DomModel<T>, C extends PsiElement>
    extends DomModelFactoryHelper<T>
    implements CachedDomModelFactory<T,M,Scope>, MultipleDomModelFactory<Scope,T,M> {
  
    private final DomModelCache<M, Scope> myCombinedModelCache;
    private final DomModelCache<List<M>, Scope> myAllModelsCache;


    protected CachedMultipleDomModelFactory(@NotNull Class<T> aClass,
                            @NotNull ModelMerger modelMerger,
                            final Project project,
                            @NonNls String name) {
      super(aClass,modelMerger);

      myCombinedModelCache = new DomModelCache<M, Scope>(project, name + " combined model") {
        @Override
        @NotNull
        protected CachedValueProvider.Result<M> computeValue(@NotNull final Scope scope) {
          final M combinedModel = computeCombinedModel(scope);
          return new CachedValueProvider.Result<>(combinedModel, computeDependencies(combinedModel, scope));
        }
      };

      myAllModelsCache = new DomModelCache<List<M>, Scope>(project, name + " models list") {
        @Override
        @NotNull
        protected CachedValueProvider.Result<List<M>> computeValue(@NotNull final Scope scope) {
          final List<M> models = computeAllModels(scope);
          return new CachedValueProvider.Result<>(models, computeDependencies(null, scope));
        }
      };
    }

    @Nullable
    public abstract M getModel(@NotNull C context);

    @Override
    @NotNull
    public List<M> getAllModels(@NotNull Scope scope) {

      final List<M> models = myAllModelsCache.getCachedValue(scope);
      if (models == null) {
        return Collections.emptyList();
      }
      else {
        return models;
      }
    }

    @Nullable
    protected abstract List<M> computeAllModels(@NotNull Scope scope);

    @Override
    @Nullable
    public M getCombinedModel(@Nullable Scope scope) {
      if (scope == null) {
        return null;
      }
      return myCombinedModelCache.getCachedValue(scope);
    }

    @Nullable
    protected M computeCombinedModel(@NotNull Scope scope) {
      final List<M> models = getAllModels(scope);
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
      return createCombinedModel(configFiles, mergedModel, firstModel, scope);
    }

    /**
     * Factory method to create combined model for given module.
     * Used by {@link #computeCombinedModel(com.intellij.openapi.module.Module)}.
     *
     * @param configFiles file set including all files for all models returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
     * @param mergedModel merged model for all models returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
     * @param firstModel the first model returned by {@link #getAllModels(com.intellij.openapi.module.Module)}.
     * @param scope
     * @return combined model.
     */
    protected abstract M createCombinedModel(Set<XmlFile> configFiles, DomFileElement<T> mergedModel, M firstModel, final Scope scope);

    @NotNull
    public Set<XmlFile> getConfigFiles(@Nullable C context) {
      if (context == null) {
        return Collections.emptySet();
      }
      final M model = getModel(context);
      if (model == null) {
        return Collections.emptySet();
      }
      else {
        return model.getConfigFiles();
      }
    }

    @Override
    @NotNull
    public Set<XmlFile> getAllConfigFiles(@NotNull Scope scope) {
      final HashSet<XmlFile> xmlFiles = new HashSet<>();
      for (M model: getAllModels(scope)) {
        xmlFiles.addAll(model.getConfigFiles());
      }
      return xmlFiles;
    }

    public List<DomFileElement<T>> getFileElements(M model) {
      final ArrayList<DomFileElement<T>> list = new ArrayList<>(model.getConfigFiles().size());
      for (XmlFile configFile: model.getConfigFiles()) {
        final DomFileElement<T> element = DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
        if (element != null) {
          list.add(element);
        }
      }
      return list;
    }

  }
