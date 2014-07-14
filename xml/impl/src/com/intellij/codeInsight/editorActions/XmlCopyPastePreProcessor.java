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
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XmlCopyPastePreProcessor implements CopyPastePreProcessor {

  private static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  @Override
  @Nullable
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  @NotNull
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement element = PsiUtilCore.getElementAtOffset(file, caretOffset);

    ASTNode node = element.getNode();
    if (node != null) {
      boolean hasMarkup = text.indexOf('>') >= 0 || text.indexOf('<') >= 0;
      if (element.getTextOffset() == caretOffset &&
          node.getElementType() == XmlTokenType.XML_END_TAG_START &&
          node.getTreePrev().getElementType() == XmlTokenType.XML_TAG_END) {

         return hasMarkup ? text : encode(text, element);
      } else {
        XmlElement parent = PsiTreeUtil.getParentOfType(element, XmlText.class, XmlAttributeValue.class);
        if (parent != null) {
          if (parent instanceof XmlText && hasMarkup) {
            return text;
          }

          if (TreeUtil.findParent(node, XmlElementType.XML_CDATA) == null &&
              TreeUtil.findParent(node, XmlElementType.XML_COMMENT) == null) {
            return encode(text, element);
          }
        }
      }
    }
    return text;
  }

  private static String encode(String text, PsiElement element) {
    ASTNode astNode = ENCODE_EACH_SYMBOL_POLICY.encodeXmlTextContents(text, element);
    return astNode.getTreeParent().getText();
  }
}
