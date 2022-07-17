// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultDomAnnotator implements Annotator {
  @Nullable
  private static DomElement getDomElement(PsiElement psiElement, DomManager myDomManager) {
    if (psiElement instanceof XmlTag) {
      return myDomManager.getDomElement((XmlTag)psiElement);
    }
    if (psiElement instanceof XmlAttribute) {
      return myDomManager.getDomElement((XmlAttribute)psiElement);
    }
    return null;
  }

  public <T extends DomElement> void runInspection(@Nullable DomElementsInspection<T> inspection, DomFileElement<T> fileElement, List<Annotation> toFill) {
    if (inspection == null) {
      return;
    }

    DomElementAnnotationsManagerImpl annotationsManager = getAnnotationsManager(fileElement);
    if (annotationsManager.isHolderUpToDate(fileElement) && annotationsManager.getProblemHolder(fileElement).isInspectionCompleted(inspection)) {
      return;
    }

    DomElementAnnotationHolderImpl annotationHolder = new DomElementAnnotationHolderImpl(true, fileElement);
    inspection.checkFileElement(fileElement, annotationHolder);
    //noinspection unchecked
    annotationsManager.appendProblems(fileElement, annotationHolder, (Class<? extends DomElementsInspection<?>>)inspection.getClass());
    for (final DomElementProblemDescriptor descriptor : annotationHolder) {
      toFill.addAll(descriptor.getAnnotations());
    }
    toFill.addAll(annotationHolder.getAnnotations());
  }

  protected DomElementAnnotationsManagerImpl getAnnotationsManager(DomElement element) {
    return (DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(element.getManager().getProject());
  }


  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof XmlTag) && !(psiElement instanceof XmlAttribute)) {
      return;
    }

    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    DomManagerImpl domManager = DomManagerImpl.getDomManager(file.getProject());
    DomFileDescription<?> description = domManager.getDomFileDescription(file);
    if (description != null) {
      DomElement domElement = getDomElement(psiElement, domManager);
      if (domElement != null) {
        DomFileElement<DomElement> root = DomUtil.getFileElement(domElement);
        DomElementsInspection<DomElement> inspection = getAnnotationsManager(domElement).getMockInspection(root);
        //noinspection unchecked
        runInspection(inspection, root, (List<Annotation>)holder);
      }
    }
  }
}
