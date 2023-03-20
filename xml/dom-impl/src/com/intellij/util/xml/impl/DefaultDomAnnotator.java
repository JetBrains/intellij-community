// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolderImpl;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultDomAnnotator implements Annotator {
  public <T extends DomElement> void runInspection(@NotNull DomElementsInspection<DomElement> inspection,
                                                   @NotNull DomFileElement<DomElement> fileElement,
                                                   @NotNull AnnotationHolder toFill) {
    DomElementAnnotationsManagerImpl annotationsManager = getAnnotationsManager(fileElement.getFile().getProject());
    if (annotationsManager.isHolderUpToDate(fileElement) && annotationsManager.getProblemHolder(fileElement).isInspectionCompleted(inspection)) {
      return;
    }

    DomElementAnnotationHolderImpl annotationHolder = new DomElementAnnotationHolderImpl(true, fileElement, toFill);
    inspection.checkFileElement(fileElement, annotationHolder);
    //noinspection unchecked
    annotationsManager.appendProblems(fileElement, annotationHolder, (Class<? extends DomElementsInspection<?>>)inspection.getClass());
  }

  @NotNull
  protected DomElementAnnotationsManagerImpl getAnnotationsManager(@NotNull Project project) {
    return (DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(project);
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof XmlFile)) {
      return;
    }
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    Project project = file.getProject();
    DomManager domManager = DomManager.getDomManager(project);
    DomFileDescription<?> description = file instanceof XmlFile ? domManager.getDomFileDescription((XmlFile)file) : null;
    if (description != null) {
      psiElement.accept(new XmlRecursiveElementWalkingVisitor(){
        @Override
        public void visitXmlElement(@NotNull XmlElement element) {
          DomElement domElement = getDomElement(element, domManager);
          if (domElement != null) {
            DomFileElement<DomElement> root = DomUtil.getFileElement(domElement);
            DomElementsInspection<DomElement> inspection = getAnnotationsManager(project).getMockInspection(root);
            if (inspection != null) {
              runInspection(inspection, root, holder);
            }
          }
          else {
            super.visitXmlElement(element);
          }
        }
      });
    }
  }

  @Nullable
  private static DomElement getDomElement(@NotNull PsiElement psiElement, @NotNull DomManager myDomManager) {
    if (psiElement instanceof XmlTag) {
      return myDomManager.getDomElement((XmlTag)psiElement);
    }
    if (psiElement instanceof XmlAttribute) {
      return myDomManager.getDomElement((XmlAttribute)psiElement);
    }
    return null;
  }
}
