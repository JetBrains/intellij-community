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
package com.jetbrains.rest.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.RestElementTypes;
import com.jetbrains.rest.RestTokenTypes;

/**
 * User : catherine
 */
public class RestASTFactory implements RestTokenTypes, RestElementTypes {

  public PsiElement create(ASTNode node) {
    IElementType type = node.getElementType();

    if (type == DIRECTIVE_BLOCK) {
      return new RestDirectiveBlock(node);
    }
    if (type == REFERENCE_NAME) {
      return new RestReference(node);
    }
    if (type == REFERENCE_TARGET) {
      return new RestReferenceTarget(node);
    }
    if (type == TITLE) {
      return new RestTitle(node);
    }
    if (type == FIELD) {
      return new RestRole(node);
    }
    if (type == FIELD_LIST) {
      return new RestFieldList(node);
    }
    if (type == INLINE_BLOCK) {
      return new RestInlineBlock(node);
    }
    if (type == LINE_TEXT) {
      return new RestLine(node);
    }
    return new ASTWrapperPsiElement(node);
  }
}
