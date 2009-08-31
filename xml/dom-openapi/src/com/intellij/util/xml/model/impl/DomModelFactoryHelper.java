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
