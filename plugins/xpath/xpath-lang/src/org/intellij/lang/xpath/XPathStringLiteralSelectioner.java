/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.intellij.lang.xpath;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;

import java.util.Collections;
import java.util.List;

/**
 * User: Maxim.Mossienko
 * Date: 08.10.2009
 * Time: 21:06:04
 */
public class XPathStringLiteralSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(PsiElement e) {
    ASTNode astNode = e.getNode();
    return astNode != null &&
           (astNode.getElementType() == XPathTokenTypes.STRING_LITERAL);
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    TextRange elem = e.getTextRange();
    if (elem.getLength() > 2) {
      return new SmartList<>(
        new TextRange(elem.getStartOffset() + 1, elem.getEndOffset() - 1)
      );
    }
    return Collections.emptyList();
  }
}
