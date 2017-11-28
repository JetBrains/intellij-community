/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlNamespaceHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
* @author peter
*/
public class XmlAttributeInsertHandler implements InsertHandler<LookupElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.XmlAttributeInsertHandler");

  public static final XmlAttributeInsertHandler INSTANCE = new XmlAttributeInsertHandler();

  private final String myNamespaceToInsert;

  public XmlAttributeInsertHandler() {
    this(null);
  }

  public XmlAttributeInsertHandler(@Nullable String namespaceToInsert) {
    myNamespaceToInsert = namespaceToInsert;
  }

  @Override
  public void handleInsert(final InsertionContext context, final LookupElement item) {
    final Editor editor = context.getEditor();

    final Document document = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    final PsiFile file = context.getFile();

    final CharSequence chars = document.getCharsSequence();
    final String quote = XmlEditUtil.getAttributeQuote(file);
    final boolean insertQuotes = WebEditorOptions.getInstance().isInsertQuotesForAttributeValue() && StringUtil.isNotEmpty(quote);
    final boolean hasQuotes = CharArrayUtil.regionMatches(chars, caretOffset, "=\"") ||
                              CharArrayUtil.regionMatches(chars, caretOffset, "='");
    if (!hasQuotes) {
      PsiElement fileContext = file.getContext();
      String toInsert = null;

      if(fileContext != null) {
        if (fileContext.getText().startsWith("\"")) toInsert = "=''";
        if (fileContext.getText().startsWith("\'")) toInsert = "=\"\"";
      }
      if (toInsert == null) {
        toInsert = "=" + quote + quote;
      }

      if (!insertQuotes) toInsert = "=";

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

    editor.getCaretModel().moveToOffset(caretOffset + (insertQuotes || hasQuotes ? 2 : 1));
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
    AutoPopupController.getInstance(editor.getProject()).scheduleAutoPopup(editor);

    if (myNamespaceToInsert != null && file instanceof XmlFile) {
      final PsiElement element = file.findElementAt(context.getStartOffset());
      final XmlTag tag = element != null ? PsiTreeUtil.getParentOfType(element, XmlTag.class) : null;

      if (tag != null) {
        String prefix = ExtendedTagInsertHandler.suggestPrefix((XmlFile)file, myNamespaceToInsert);

        if (prefix != null) {
          prefix = makePrefixUnique(prefix, tag);
          final XmlNamespaceHelper helper = XmlNamespaceHelper.getHelper(context.getFile());

          if (helper != null) {
            final Project project = context.getProject();
            PsiDocumentManager.getInstance(project).commitDocument(document);
            qualifyWithPrefix(prefix, element);
            helper.insertNamespaceDeclaration((XmlFile)file, editor, Collections.singleton(
              myNamespaceToInsert), prefix, null);
          }
        }
      }
    }
  }

  private static void qualifyWithPrefix(@NotNull String namespacePrefix, @NotNull PsiElement context) {
    final PsiElement parent = context.getParent();

    if (parent instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute)parent;
      final String prefix = attribute.getNamespacePrefix();

      if (!prefix.equals(namespacePrefix) && StringUtil.isNotEmpty(namespacePrefix)) {
        final String name = namespacePrefix + ":" + attribute.getLocalName();
        try {
          attribute.setName(name);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  @NotNull
  private static String makePrefixUnique(@NotNull String basePrefix, @NotNull XmlTag context) {
    if (context.getNamespaceByPrefix(basePrefix).isEmpty()) {
      return basePrefix;
    }
    int i = 1;

    while (!context.getNamespaceByPrefix(basePrefix + i).isEmpty()) {
      i++;
    }
    return basePrefix + i;
  }
}
