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

import com.intellij.util.xml.ModelMerger;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
public class DomModelFactoryHelper<T extends DomElement> {
  protected final Class<T> myClass;
  protected final ModelMerger myModelMerger;

  public DomModelFactoryHelper(@NotNull Class<T> aClass, @NotNull ModelMerger modelMerger) {
    myClass = aClass;
    myModelMerger = modelMerger;
  }

  @Nullable
  public T getDom(@NotNull XmlFile configFile) {
    final DomFileElement<T> element = getDomRoot(configFile);
    return element == null ? null : element.getRootElement();
  }

  @Nullable
  public DomFileElement<T> getDomRoot(@NotNull XmlFile configFile) {
    return DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
  }

  public Class<T> getDomModelClass() {
    return myClass;
  }

  public ModelMerger getModelMerger() {
    return myModelMerger;
  }
}
