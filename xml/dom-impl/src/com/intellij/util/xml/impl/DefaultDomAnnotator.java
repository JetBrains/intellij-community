// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.AnnotationBuilder;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    List<DomElementProblemDescriptor> problemDescriptors =
      annotationsManager.appendProblems(fileElement, annotationHolder, (Class<? extends DomElementsInspection<?>>)inspection.getClass());

    for (final DomElementProblemDescriptor descriptor : problemDescriptors) {
      DomElementsHighlightingUtil.createProblemDescriptors(descriptor, s -> {
        AnnotationBuilder builder = toFill.newAnnotation(descriptor.getHighlightSeverity(), descriptor.getDescriptionTemplate())
          .range(s.first.shiftRight(s.second.getTextOffset()));

        ProblemDescriptor problemDescriptor = ContainerUtil.getFirstItem(annotationsManager.createProblemDescriptors(
          InspectionManager.getInstance(fileElement.getFile().getProject()), descriptor));

        LocalQuickFix[] fixes = descriptor.getFixes();
        if (problemDescriptor != null && fixes != null) {
          for (LocalQuickFix fix : fixes) {
            builder = builder.newLocalQuickFix(fix, problemDescriptor).registerFix();
          }
        }
        builder.create();
        return null;
      });
    }
  }


  protected @NotNull DomElementAnnotationsManagerImpl getAnnotationsManager(@NotNull Project project) {
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

  private static @Nullable DomElement getDomElement(@NotNull PsiElement psiElement, @NotNull DomManager myDomManager) {
    if (psiElement instanceof XmlTag) {
      return myDomManager.getDomElement((XmlTag)psiElement);
    }
    if (psiElement instanceof XmlAttribute) {
      return myDomManager.getDomElement((XmlAttribute)psiElement);
    }
    return null;
  }
}
