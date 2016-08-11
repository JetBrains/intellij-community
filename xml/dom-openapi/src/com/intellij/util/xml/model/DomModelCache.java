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

package com.intellij.util.xml.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class DomModelCache<T, H extends UserDataHolder> {

  private final Key<CachedValue<T>> myKey;
  private final Project myProject;

  public DomModelCache(Project project, @NonNls String keyName) {
    myProject = project;
    myKey = new Key<>(keyName);
  }

  @Nullable
  public T getCachedValue(final @NotNull H dataHolder) {
    CachedValue<T> cachedValue = dataHolder.getUserData(myKey);
    if (cachedValue == null) {
      final CachedValueProvider<T> myProvider = () -> computeValue(dataHolder);
      final CachedValuesManager manager = CachedValuesManager.getManager(myProject);
      cachedValue = manager.createCachedValue(myProvider, false);
      dataHolder.putUserData(myKey, cachedValue);
    }
    return cachedValue.getValue();
  }

  @NotNull
  protected abstract CachedValueProvider.Result<T> computeValue(@NotNull H dataHolder);
}
