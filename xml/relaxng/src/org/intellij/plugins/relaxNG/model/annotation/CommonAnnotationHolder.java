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

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class CommonAnnotationHolder<C> {
  public static <T extends DomElement> CommonAnnotationHolder<T> create(DomElementAnnotationHolder holder) {
    return new DomHolderAdapter<>(holder);
  }

  public static <T extends PsiElement> CommonAnnotationHolder<T> create(AnnotationHolder holder) {
    return new HolderAdapter<>(holder);
  }

  public abstract void createAnnotation(@NotNull HighlightSeverity severity,
                                        @NotNull C element,
                                        @Nullable @InspectionMessage String message,
                                        @Nullable GutterIconRenderer renderer);

  private static class DomHolderAdapter<T extends DomElement> extends CommonAnnotationHolder<T> {
    private final DomElementAnnotationHolder myHolder;

    DomHolderAdapter(DomElementAnnotationHolder holder) {
      myHolder = holder;
    }

    @Override
    public void createAnnotation(@NotNull HighlightSeverity severity,
                                 @NotNull DomElement element,
                                 @Nullable @InspectionMessage String message,
                                 @Nullable GutterIconRenderer renderer) {
      AnnotationHolder annotationHolder = myHolder.getAnnotationHolder();
      final XmlElement xmlElement = element.getXmlElement();
      if (xmlElement == null) return;

      AnnotationBuilder builder = message == null ? annotationHolder.newSilentAnnotation(severity) : annotationHolder.newAnnotation(severity, message);
      builder = builder.range(xmlElement.getNavigationElement());

      if (message != null) {
        builder = builder.tooltip(message);  // no tooltip by default??
      }
      if (renderer != null) {
        builder = builder.gutterIconRenderer(renderer);
      }
      builder.create();
    }
  }

  private static class HolderAdapter<T extends PsiElement> extends CommonAnnotationHolder<T> {
    private final AnnotationHolder myHolder;

    HolderAdapter(AnnotationHolder holder) {
      myHolder = holder;
    }

    @Override
    public void createAnnotation(@NotNull HighlightSeverity severity,
                                 @NotNull T element,
                                 @Nullable @InspectionMessage String message,
                                 @Nullable GutterIconRenderer renderer) {
      AnnotationBuilder builder = message == null ? myHolder.newSilentAnnotation(severity) : myHolder.newAnnotation(severity, message);
      if (renderer != null) {
        builder = builder.gutterIconRenderer(renderer);
      }
      builder.create();
    }
  }
}
