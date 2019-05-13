/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

/**
 * @author peter
 */
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

  public <T extends DomElement> void runInspection(@Nullable final DomElementsInspection<T> inspection, final DomFileElement<T> fileElement, List<? super Annotation> toFill) {
    if (inspection == null) return;
    DomElementAnnotationsManagerImpl annotationsManager = getAnnotationsManager(fileElement);
    if (DomElementAnnotationsManagerImpl.isHolderUpToDate(fileElement) && annotationsManager.getProblemHolder(fileElement).isInspectionCompleted(inspection)) return;

    DomElementAnnotationHolderImpl annotationHolder = new DomElementAnnotationHolderImpl(true, fileElement);
    inspection.checkFileElement(fileElement, annotationHolder);
    annotationsManager.appendProblems(fileElement, annotationHolder, inspection.getClass());
    for (final DomElementProblemDescriptor descriptor : annotationHolder) {
      toFill.addAll(descriptor.getAnnotations());
    }
    toFill.addAll(annotationHolder.getAnnotations());
  }

  protected DomElementAnnotationsManagerImpl getAnnotationsManager(final DomElement element) {
    return (DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(element.getManager().getProject());
  }


  @Override
  public void annotate(@NotNull final PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof XmlTag) &&
        !(psiElement instanceof XmlAttribute)) {
      return;
    }

    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    final DomManagerImpl domManager = DomManagerImpl.getDomManager(file.getProject());
    final DomFileDescription description = domManager.getDomFileDescription(file);
    if (description != null) {
      final DomElement domElement = getDomElement(psiElement, domManager);
      if (domElement != null) {
        final DomFileElement root = DomUtil.getFileElement(domElement);
        runInspection(getAnnotationsManager(domElement).getMockInspection(root), root, (List<Annotation>)holder);
      }
    }
  }
}
