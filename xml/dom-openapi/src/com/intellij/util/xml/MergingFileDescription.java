// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MergingFileDescription<T extends DomElement> extends DomFileDescription<T>{
  private ModelMerger myMerger;

  protected MergingFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName) {
    super(rootElementClass, rootTagName);
  }

  @NotNull
  protected abstract Set<XmlFile> getFilesToMerge(DomElement element);

  @Override
  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    final DomFileElement<T> element = DomUtil.getFileElement(reference);
    return getMergedRoot(element);
  }

  public final T getMergedRoot(DomFileElement<T> element) {
    final DomManager domManager = element.getManager();

    XmlFile xmlFile = element.getFile();
    Set<XmlFile> files = new HashSet<>();
    files.add(xmlFile);
    final XmlFile originalFile = (XmlFile)xmlFile.getOriginalFile();
    if (originalFile != xmlFile) {
      final DomFileElement originalElement = domManager.getFileElement(originalFile);
      if (originalElement != null) {
        element = originalElement;
      }
    }

    files.addAll(getFilesToMerge(element));


    ArrayList<T> roots = new ArrayList<>(files.size());
    for (XmlFile file: files) {
      final DomFileElement<T> fileElement = domManager.getFileElement(file);
      if (fileElement != null) {
        roots.add(fileElement.getRootElement());
      }
    }

    if (roots.size() == 1) {
      return roots.iterator().next();
    }

    if (myMerger == null) {
      myMerger = DomService.getInstance().createModelMerger();
    }
    return myMerger.mergeModels(getRootElementClass(), roots);
  }

  @Override
  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    final List<JavaMethod> methods = DomUtil.getFixedPath(element.getParent());
    if (methods == null) return super.getIdentityScope(element);

    final DomFileElement<T> root = DomUtil.getFileElement(element);
    Object o = getMergedRoot(root);
    for (final JavaMethod method : methods) {
      o = method.invoke(o, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
    }
    return (DomElement)o;
  }

  @Override
  public boolean isAutomaticHighlightingEnabled() {
    return false;
  }
}
