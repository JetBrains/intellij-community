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
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.model.DomModel;
import com.intellij.util.xml.model.DomModelCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public abstract class CachedSimpleDomModelFactory<T extends DomElement, M extends DomModel<T>, Scope extends UserDataHolder> extends
                                                                                                                             SimpleDomModelFactory<T, M>
    implements CachedDomModelFactory<T,M,Scope> {

  private final DomModelCache<M, XmlFile> myModelCache;

  protected CachedSimpleDomModelFactory(@NotNull Class<T> aClass,
                          @NotNull ModelMerger modelMerger,
                          final Project project,
                          @NonNls String name) {
    super(aClass, modelMerger);

    myModelCache = new DomModelCache<M, XmlFile>(project, name + " model") {
       @Override
       @NotNull
       protected CachedValueProvider.Result<M> computeValue(@NotNull XmlFile file) {
         file = (XmlFile)file.getOriginalFile();

         final Scope scope = getModelScope(file);
         final M model = computeModel(file, scope);
         return new CachedValueProvider.Result<>(model, computeDependencies(model, scope));
      }
    };
  }

  @Override
  @Nullable
  public M getModelByConfigFile(@Nullable XmlFile psiFile) {
    if (psiFile == null) {
      return null;
    }
    return myModelCache.getCachedValue(psiFile);
  }

  @Nullable
  protected abstract M computeModel(@NotNull XmlFile psiFile, @Nullable Scope scope);
}
