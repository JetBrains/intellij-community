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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.HtmlUtil;

/**
* @author peter
*/
public class XmlAttributeInsertHandler implements InsertHandler<LookupElement> {
  public static final XmlAttributeInsertHandler INSTANCE = new XmlAttributeInsertHandler();

  public void handleInsert(InsertionContext context, LookupElement item) {
    final Editor editor = context.getEditor();

    final Document document = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document);
    if (file.getLanguage() == HTMLLanguage.INSTANCE &&
        HtmlUtil.isSingleHtmlAttribute((String)item.getObject())) {
      return;
    }

    final CharSequence chars = document.getCharsSequence();
    if (!CharArrayUtil.regionMatches(chars, caretOffset, "=\"") && !CharArrayUtil.regionMatches(chars, caretOffset, "='")) {
      PsiElement fileContext = file.getContext();
      String toInsert= "=\"\"";

      if(fileContext != null) {
        if (fileContext.getText().startsWith("\"")) toInsert = "=''";
      }
      
      if (caretOffset >= document.getTextLength() || "/> \n\t\r".indexOf(document.getCharsSequence().charAt(caretOffset)) < 0) {
        document.insertString(caretOffset, toInsert + " ");
      }
      else {
        document.insertString(caretOffset, toInsert);
      }

      if ('=' == context.getCompletionChar()) {
        context.setAddCompletionChar(false); // IDEA-19449
      }
    }

    editor.getCaretModel().moveToOffset(caretOffset + 2);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
