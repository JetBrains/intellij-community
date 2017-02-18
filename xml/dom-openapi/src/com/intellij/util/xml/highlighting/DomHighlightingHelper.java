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
package com.intellij.util.xml.highlighting;

import com.intellij.openapi.extensions.Extensions;
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
  private final DomCustomAnnotationChecker[] myCustomCheckers = Extensions.getExtensions(DomCustomAnnotationChecker.EP_NAME);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkRequired(DomElement element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkResolveProblems(GenericDomValue element, DomElementAnnotationHolder holder);

  @NotNull
  public abstract List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, DomElementAnnotationHolder holder);

  public abstract void runAnnotators(DomElement element, DomElementAnnotationHolder holder, Class<? extends DomElement> rootClass);

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
    for (final DomCustomAnnotationChecker<?> checker : myCustomCheckers) {
      final List<DomElementProblemDescriptor> list = checkAnno(element, checker, holder);
      if (!list.isEmpty()) {
        if (result == null) result = new SmartList<>();
        result.addAll(list);
      }
    }
    return result == null ? Collections.<DomElementProblemDescriptor>emptyList() : result;
  }

  private <T extends Annotation> List<DomElementProblemDescriptor> checkAnno(final DomElement element, final DomCustomAnnotationChecker<T> checker, DomElementAnnotationHolder holder) {
    final T annotation = element.getAnnotation(checker.getAnnotationClass());
    return annotation != null ? checker.checkForProblems(annotation, element, holder, this) : Collections.<DomElementProblemDescriptor>emptyList();
  }
}
