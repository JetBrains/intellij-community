/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.annotation;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import org.jetbrains.annotations.NotNull;

abstract class CommonAnnotationHolder<C> {
  public static <T extends DomElement> CommonAnnotationHolder<T> create(DomElementAnnotationHolder holder) {
    return new DomHolderAdapter<>(holder);
  }

  public static <T extends PsiElement> CommonAnnotationHolder<T> create(AnnotationHolder holder) {
    return new HolderAdapter<>(holder);
  }

  public abstract Annotation createAnnotation(C element, @NotNull HighlightSeverity severity, String message);

  private static class DomHolderAdapter<T extends DomElement> extends CommonAnnotationHolder<T> {
    private final DomElementAnnotationHolder myHolder;

    DomHolderAdapter(DomElementAnnotationHolder holder) {
      myHolder = holder;
    }

    @Override
    public Annotation createAnnotation(DomElement element, @NotNull HighlightSeverity severity, String message) {
      final Annotation annotation = myHolder.createAnnotation(element, severity, message);
      annotation.setTooltip(message);  // no tooltip by default??
      return annotation;
    }
  }

  private static class HolderAdapter<T extends PsiElement> extends CommonAnnotationHolder<T> {
    private final AnnotationHolder myHolder;

    HolderAdapter(AnnotationHolder holder) {
      myHolder = holder;
    }

    @Override
    public Annotation createAnnotation(T element, @NotNull HighlightSeverity severity, String message) {
      if (severity == HighlightSeverity.ERROR) {
        return myHolder.createErrorAnnotation(element, message);
      } else if (severity == HighlightSeverity.WARNING) {
        return myHolder.createWarningAnnotation(element, message);
      } else if (severity == HighlightSeverity.WEAK_WARNING) {
        return myHolder.createWeakWarningAnnotation(element, message);
      } else {
        return myHolder.createInfoAnnotation(element, message);
      }
    }
  }
}
