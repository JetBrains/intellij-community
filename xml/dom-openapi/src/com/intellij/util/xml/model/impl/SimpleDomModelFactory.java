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

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.model.DomModel;
import com.intellij.util.xml.model.SimpleModelFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class SimpleDomModelFactory<T extends DomElement, M extends DomModel<T>> extends DomModelFactoryHelper<T> implements
                                                                                                                         SimpleModelFactory<T, M> {

  public SimpleDomModelFactory(@NotNull Class<T> aClass, @NotNull ModelMerger modelMerger) {
    super(aClass, modelMerger);
  }

  @Override
  @Nullable
  public DomFileElement<T> createMergedModelRoot(Set<XmlFile> configFiles) {
    List<DomFileElement<T>> configs = new ArrayList<>(configFiles.size());
    for (XmlFile configFile : configFiles) {
      ContainerUtil.addIfNotNull(configs, getDomRoot(configFile));
    }
    return configs.isEmpty() ? null : getModelMerger().mergeModels(DomFileElement.class, configs);
  }
}
