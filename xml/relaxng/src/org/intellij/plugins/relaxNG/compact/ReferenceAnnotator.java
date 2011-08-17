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

package org.intellij.plugins.relaxNG.compact;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 10.08.2007
*/
public class ReferenceAnnotator extends RncElementVisitor implements Annotator {
  private AnnotationHolder myHolder;

  public synchronized void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    myHolder = holder;
    try {
      psiElement.accept(this);
    } finally {
      myHolder = null;
    }
  }

  @Override
  public void visitInclude(RncInclude include) {
    checkReferences(include.getReferences());
  }

  @Override
  public void visitExternalRef(RncExternalRef ref) {
    checkReferences(ref.getReferences());
  }

  @Override
  public void visitRef(RncRef pattern) {
    checkReferences(pattern.getReferences());
  }

  @Override
  public void visitParentRef(RncParentRef pattern) {
    checkReferences(pattern.getReferences());
  }

  @Override
  public void visitName(RncName name) {
    checkReferences(name.getReferences());
  }

  private void checkReferences(PsiReference[] references) {
    for (PsiReference reference : references) {
      if (!reference.isSoft()) {
        if (reference.resolve() == null) {
          if (reference instanceof PsiPolyVariantReference) {
            final PsiPolyVariantReference pvr = (PsiPolyVariantReference)reference;
            if (pvr.multiResolve(false).length == 0) {
              addError(reference);
            }
          } else {
            addError(reference);
          }
        }
      }
    }
  }

  private void addError(PsiReference reference) {
    final TextRange rangeInElement = reference.getRangeInElement();
    final TextRange range = TextRange.from(reference.getElement().getTextRange().getStartOffset()
            + rangeInElement.getStartOffset(), rangeInElement.getLength());

    final Annotation annotation;
    if (reference instanceof EmptyResolveMessageProvider) {
      final String s = ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern();
      annotation = myHolder.createErrorAnnotation(range, s != null ?
              MessageFormat.format(s, reference.getCanonicalText())
              : "Cannot resolve symbol");
    } else {
      annotation = myHolder.createErrorAnnotation(range, "Cannot resolve symbol");
    }
    annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

    if (reference instanceof QuickFixProvider) {
      final HighlightInfo info = new HighlightInfo(HighlightInfoType.WRONG_REF, annotation.getStartOffset(), annotation.getEndOffset(), "", null);
      ((QuickFixProvider)reference).registerQuickfix(info, reference);

      final List<Pair<HighlightInfo.IntentionActionDescriptor,TextRange>> ranges = info.quickFixActionRanges;
      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          annotation.registerFix(pair.first.getAction(), pair.second);
        }
      }
    }
  }
}
