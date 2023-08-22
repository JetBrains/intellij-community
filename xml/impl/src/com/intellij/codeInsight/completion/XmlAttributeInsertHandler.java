// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
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

public class XmlAttributeInsertHandler implements InsertHandler<LookupElement> {
  private static final Logger LOG = Logger.getInstance(XmlAttributeInsertHandler.class);

  public static final XmlAttributeInsertHandler INSTANCE = new XmlAttributeInsertHandler();

  private final String myNamespaceToInsert;

  public XmlAttributeInsertHandler() {
    this(null);
  }

  public XmlAttributeInsertHandler(@Nullable String namespaceToInsert) {
    myNamespaceToInsert = namespaceToInsert;
  }

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull final LookupElement item) {
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
      if (CharArrayUtil.regionMatches(chars, caretOffset, "=")) {
        document.deleteString(caretOffset, caretOffset + 1);
      }

      PsiElement fileContext = file.getContext();
      String toInsert = null;

      if(fileContext != null) {
        if (fileContext.getText().startsWith("\"")) toInsert = "=''";
        if (fileContext.getText().startsWith("'")) toInsert = "=\"\"";
      }
      if (toInsert == null) {
        toInsert = "=" + quote + quote;
      }

      if (!insertQuotes) toInsert = "=";

      if (caretOffset < document.getTextLength() && "/> \n\t\r".indexOf(document.getCharsSequence().charAt(caretOffset)) < 0) {
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
    TabOutScopesTracker.getInstance().registerEmptyScopeAtCaret(context.getEditor());
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

    if (parent instanceof XmlAttribute attribute) {
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
