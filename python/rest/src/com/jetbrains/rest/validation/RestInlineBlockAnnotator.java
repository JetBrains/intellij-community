/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.validation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.psi.RestInlineBlock;

/**
 * Looks for invalid inline block
 *
 * User : catherine
 */
public class RestInlineBlockAnnotator extends RestAnnotator {

  @Override
  public void visitInlineBlock(final RestInlineBlock node) {
    if (!node.validBlock()) {
      PsiElement el = node.getLastChild();
      if (el != null) {
        final int endOffset = node.getTextRange().getEndOffset();
        getHolder().createErrorAnnotation(TextRange.create(endOffset-1, endOffset), RestBundle.message("ANN.inline.block"));
      }
    }
  }
}
