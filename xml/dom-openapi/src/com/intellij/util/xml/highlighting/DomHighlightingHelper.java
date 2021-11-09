// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class DomHighlightingHelper {

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkRequired(DomElement element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkResolveProblems(GenericDomValue element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, DomElementAnnotationHolder holder);

  public abstract void runAnnotators(DomElement element, DomElementAnnotationHolder holder, @NotNull Class<? extends DomElement> rootClass);

  /**
   * Runs all registered {@link DomCustomAnnotationChecker}s.
   *
   * @param element Dom element to check.
   * @param holder Holder instance.
   * @return Collected problem descriptors.
   */
  @NotNull
  public List<DomElementProblemDescriptor> checkCustomAnnotations(final DomElement element, final DomElementAnnotationHolder holder) {
    List<DomElementProblemDescriptor> result = null;
    for (DomCustomAnnotationChecker<?> checker : DomCustomAnnotationChecker.EP_NAME.getExtensionList()) {
      final List<DomElementProblemDescriptor> list = checkAnno(element, checker, holder);
      if (!list.isEmpty()) {
        if (result == null) result = new SmartList<>();
        result.addAll(list);
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private <T extends Annotation> List<DomElementProblemDescriptor> checkAnno(final DomElement element, final DomCustomAnnotationChecker<T> checker, DomElementAnnotationHolder holder) {
    final T annotation = element.getAnnotation(checker.getAnnotationClass());
    return annotation != null ? checker.checkForProblems(annotation, element, holder, this) : Collections.emptyList();
  }
}
