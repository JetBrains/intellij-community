/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.rest.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.psi.RestTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Handles nodes in ReST Structure View.
 * User : catherine
 */
public class RestStructureViewElement extends PsiTreeElementBase<NavigatablePsiElement> {

  public RestStructureViewElement(NavigatablePsiElement element) {
    super(element);
  }
  @NotNull
  @Override
  public Collection<StructureViewTreeElement> getChildrenBase() {
    Collection<StructureViewTreeElement> result = new LinkedList<>();

    final NavigatablePsiElement element = getElement();
    if (element == null) {
      return result;
    }

    final PsiFile file = element.getContainingFile();
    final TextRange range = element.equals(file) ? TextRange.EMPTY_RANGE : element.getTextRange();
    Pair<Character, Character> elementAdornments = element instanceof RestTitle ? ((RestTitle)element).getAdornments() : Pair.empty();

    Pair<Character, Character> adornmentsToUse = null;
    final Collection<RestTitle> titles = PsiTreeUtil.findChildrenOfType(file, RestTitle.class);
    final HashSet<Pair<Character, Character>> usedAdornments = new HashSet<>();
    boolean skipElements = false;

    for (RestTitle child : titles) {
      final Pair<Character, Character> childAdornments = child.getAdornments();

      if (child.getTextRange().getStartOffset() >= range.getEndOffset()) {
        final Character overline = childAdornments.getFirst();
        final Character underline = childAdornments.getSecond();
        if (adornmentsToUse == null) {
          adornmentsToUse = childAdornments;
        }
        if (usedAdornments.contains(childAdornments) || underline == null) {
          break;
        }
        if (underline.equals(adornmentsToUse.getSecond()) && ((adornmentsToUse.getFirst() == null && overline == null) ||
                                                              adornmentsToUse.getFirst().equals(overline))) {
          result.add(new RestStructureViewElement(child));
        }
      }
      else {
        if (element.equals(child)) {
          skipElements = false;
        }
        else if (childAdornments.equals(elementAdornments)) {
          skipElements = true;
        }

        if (!skipElements) {
          usedAdornments.add(child.getAdornments());
        }
      }
    }
    return result;
  }

  @Nullable
  @Override
  public String getPresentableText() {
    final NavigatablePsiElement element = getElement();
    return element != null ? element.getName() : "";
  }
}
