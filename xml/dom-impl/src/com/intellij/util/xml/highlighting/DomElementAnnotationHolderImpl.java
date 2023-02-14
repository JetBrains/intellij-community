/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.ConvertContextFactory;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DomElementAnnotationHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementAnnotationHolder {
  private static final Logger LOG = Logger.getInstance(DomElementAnnotationHolderImpl.class);
  private final SmartList<Annotation> myAnnotations = new SmartList<>();
  private final boolean myOnTheFly;
  private final DomFileElement myFileElement;
  private final AnnotationHolder myAnnotationHolder;

  public DomElementAnnotationHolderImpl(boolean onTheFly, @NotNull DomFileElement fileElement, @NotNull AnnotationHolder toFill) {
    myOnTheFly = onTheFly;
    myFileElement = fileElement;
    myAnnotationHolder = toFill;
  }

  @Override
  public boolean isOnTheFly() {
    return myOnTheFly;
  }

  @NotNull
  @Override
  public DomFileElement<?> getFileElement() {
    return myFileElement;
  }

  @Override
  @NotNull
  public DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                                   @Nullable @InspectionMessage String message,
                                                   @NotNull LocalQuickFix @NotNull ... fixes) {
    return createProblem(domElement, HighlightSeverity.ERROR, message, fixes);
  }

  @Override
  @NotNull
  public DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                                   DomCollectionChildDescription childDescription,
                                                   @Nullable @InspectionMessage String message) {
    return addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, HighlightSeverity.ERROR, childDescription));
  }

  @Override
  @NotNull
  public final DomElementProblemDescriptor createProblem(@NotNull DomElement domElement, HighlightSeverity highlightType, @InspectionMessage String message) {
    return createProblem(domElement, highlightType, message, LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public DomElementProblemDescriptor createProblem(@NotNull final DomElement domElement,
                                                   final HighlightSeverity highlightType,
                                                   @InspectionMessage String message,
                                                   final @NotNull LocalQuickFix @NotNull ... fixes) {
    return createProblem(domElement, highlightType, message, null, fixes);
  }

  @Override
  public DomElementProblemDescriptor createProblem(@NotNull final DomElement domElement,
                                                   final HighlightSeverity highlightType,
                                                   @InspectionMessage String message,
                                                   final TextRange textRange,
                                                   final @NotNull LocalQuickFix @NotNull ... fixes) {
    return addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType, textRange, null, fixes));
  }

  @Override
  public DomElementProblemDescriptor createProblem(@NotNull DomElement domElement,
                                                   ProblemHighlightType highlightType,
                                                   @InspectionMessage String message,
                                                   @Nullable TextRange textRange,
                                                   @NotNull LocalQuickFix @NotNull ... fixes) {
    return addProblem(new DomElementProblemDescriptorImpl(domElement, message, HighlightSeverity.ERROR, textRange, highlightType, fixes));
  }

  @Override
  @NotNull
  public DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference) {
    return addProblem(new DomElementResolveProblemDescriptorImpl(element, reference, getQuickFixes(element, reference)));
  }

  @Override
  public @NotNull AnnotationHolder getAnnotationHolder() {
    return myAnnotationHolder;
  }

  public final SmartList<Annotation> getAnnotations() {
    return myAnnotations;
  }

  @Override
  public int getSize() {
    return size();
  }

  private @NotNull LocalQuickFix @NotNull [] getQuickFixes(final GenericDomValue element, PsiReference reference) {
    if (!myOnTheFly) return LocalQuickFix.EMPTY_ARRAY;

    final List<LocalQuickFix> result = new SmartList<>();
    final Converter converter = WrappingConverter.getDeepestConverter(element.getConverter(), element);
    if (converter instanceof ResolvingConverter resolvingConverter) {
      ContainerUtil
        .addAll(result, resolvingConverter.getQuickFixes(ConvertContextFactory.createConvertContext(DomManagerImpl.getDomInvocationHandler(element))));
    }
    if (reference instanceof LocalQuickFixProvider) {
      final LocalQuickFix[] localQuickFixes = ((LocalQuickFixProvider)reference).getQuickFixes();
      if (localQuickFixes != null) {
        ContainerUtil.addAll(result, localQuickFixes);
      }
    }
    return result.isEmpty() ? LocalQuickFix.EMPTY_ARRAY : result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  public <T extends DomElementProblemDescriptor> T addProblem(final T problemDescriptor) {
    add(problemDescriptor);
    return problemDescriptor;
  }

}
