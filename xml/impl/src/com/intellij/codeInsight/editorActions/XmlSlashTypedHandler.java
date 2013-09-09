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
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public class XmlSlashTypedHandler extends TypedHandlerDelegate implements XmlTokenType {
  public Result beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile editedFile, final FileType fileType) {
    if ((editedFile.getLanguage() instanceof XMLLanguage || editedFile.getViewProvider().getBaseLanguage() instanceof XMLLanguage) && c == '/') {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      final int offset = editor.getCaretModel().getOffset();
      FileViewProvider provider = file.getViewProvider();
      PsiElement element = provider.findElementAt(offset, XMLLanguage.class);

      if (element instanceof XmlToken) {
        final IElementType tokenType = ((XmlToken)element).getTokenType();

        if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END &&
            offset == element.getTextOffset()
           ) {
          editor.getCaretModel().moveToOffset(offset + 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          return Result.STOP;
        } else if (tokenType == XmlTokenType.XML_TAG_END &&
                   offset == element.getTextOffset()
                  ) {
          final ASTNode parentNode = element.getParent().getNode();
          final ASTNode child = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parentNode);

          if (child != null && offset + 1 == child.getTextRange().getStartOffset()) {
            editor.getDocument().replaceString(offset + 1, parentNode.getTextRange().getEndOffset(),"");
          }
        }
      }
    }
    return Result.CONTINUE;
  }

  public Result charTyped(final char c, final Project project, @NotNull final Editor editor, @NotNull final PsiFile editedFile) {
    if ((editedFile.getLanguage() instanceof XMLLanguage || editedFile.getViewProvider().getBaseLanguage() instanceof XMLLanguage) && c == '/') {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      FileViewProvider provider = file.getViewProvider();
      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = provider.findElementAt(offset - 1, XMLLanguage.class);
      if (element == null) return Result.CONTINUE;
      if (!(element.getLanguage() instanceof XMLLanguage)) return Result.CONTINUE;

      ASTNode prevLeaf = element.getNode();
      if (prevLeaf == null) return Result.CONTINUE;
      final String prevLeafText = prevLeaf.getText();
      if ("</".equals(prevLeafText) && prevLeaf.getElementType() == XML_END_TAG_START) {
        XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
        if (tag != null && StringUtil.isNotEmpty(tag.getName()) && TreeUtil.findSibling(prevLeaf, XmlTokenType.XML_NAME) == null) {
          EditorModificationUtil.insertStringAtCaret(editor, tag.getName() + ">");
          return Result.STOP;
        }
      }
      if (!"/".equals(prevLeafText.trim())) return Result.CONTINUE;

      while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
      if(prevLeaf instanceof OuterLanguageElement) {
        element = file.getViewProvider().findElementAt(offset - 1, file.getLanguage());
        prevLeaf = element.getNode();
        while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
      }
      if(prevLeaf == null) return Result.CONTINUE;

      XmlTag tag = PsiTreeUtil.getParentOfType(prevLeaf.getPsi(), XmlTag.class);
      if(tag == null) { // prevLeaf maybe in one tree and element in another
        PsiElement element2 = provider.findElementAt(prevLeaf.getStartOffset(), XMLLanguage.class);
        tag = PsiTreeUtil.getParentOfType(element2, XmlTag.class);
        if (tag == null) return Result.CONTINUE;
      }

      final XmlToken startToken = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_START_TAG_START);
      if (startToken == null || !startToken.getText().equals("<")) return Result.CONTINUE;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return Result.CONTINUE;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return Result.CONTINUE;
      if (PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) != null) return Result.CONTINUE;

      EditorModificationUtil.insertStringAtCaret(editor, ">");
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}