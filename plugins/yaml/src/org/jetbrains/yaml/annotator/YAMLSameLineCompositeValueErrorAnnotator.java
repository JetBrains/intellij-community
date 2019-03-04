// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;

import java.util.Collection;
import java.util.List;

public class YAMLSameLineCompositeValueErrorAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof YAMLKeyValue)) {
      return;
    }
    YAMLKeyValue keyValue = ((YAMLKeyValue)element);
    if (hasOuterElements(keyValue)) {
      return;
    }

    PsiFile file = keyValue.getContainingFile();
    if (file == null) {
      return;
    }

    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return;
    }
    CharSequence documentContent = document.getCharsSequence();

    YAMLValue value = keyValue.getValue();
    if (value instanceof YAMLBlockMappingImpl) {
      YAMLKeyValue firstSubValue = ((YAMLBlockMappingImpl)value).getFirstKeyValue();
      if (psiAreAtTheSameLine(keyValue, firstSubValue, documentContent)) {
        reportAboutSameLine(holder, value);
      }
    }
    if (value instanceof YAMLBlockSequenceImpl) {
      List<YAMLSequenceItem> items = ((YAMLBlockSequenceImpl)value).getItems();
      if (items.isEmpty()) {
        // a very strange situation: a sequence without any item
        return;
      }
      YAMLSequenceItem firstItem = items.get(0);
      if (psiAreAtTheSameLine(keyValue, firstItem, documentContent)) {
        reportAboutSameLine(holder, value);
      }
    }
  }

  private static void reportAboutSameLine(@NotNull final AnnotationHolder holder, @NotNull YAMLValue value) {
    holder.createErrorAnnotation(value, YAMLBundle.message("annotator.same.line.composed.value.message"));
  }

  private static boolean psiAreAtTheSameLine(@NotNull PsiElement psi1, @NotNull PsiElement psi2, @NotNull CharSequence documentContent) {
    int start1 = psi1.getTextOffset();
    int start2 = psi2.getTextOffset();
    if (start2 >= documentContent.length()) {
      // It is a kind of magic
      return false;
    }

    return !StringUtil.containsLineBreak(documentContent.subSequence(start1,start2));
  }

  private static boolean hasOuterElements(PsiElement element) {
    Collection<OuterLanguageElement> outerElements = PsiTreeUtil.findChildrenOfType(element, OuterLanguageElement.class);
    return !outerElements.isEmpty();
  }
}
