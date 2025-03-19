// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.model.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ModelMerger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DomModelFactoryHelper<T extends DomElement> {
  protected final Class<T> myClass;
  protected final ModelMerger myModelMerger;

  public DomModelFactoryHelper(@NotNull Class<T> aClass, @NotNull ModelMerger modelMerger) {
    myClass = aClass;
    myModelMerger = modelMerger;
  }

  public @Nullable T getDom(@NotNull XmlFile configFile) {
    final DomFileElement<T> element = getDomRoot(configFile);
    return element == null ? null : element.getRootElement();
  }

  public @Nullable DomFileElement<T> getDomRoot(@NotNull XmlFile configFile) {
    return DomManager.getDomManager(configFile.getProject()).getFileElement(configFile, myClass);
  }

  public Class<T> getDomModelClass() {
    return myClass;
  }

  public ModelMerger getModelMerger() {
    return myModelMerger;
  }
}
