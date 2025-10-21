// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class HtmlQuotesConverter extends XmlRecursiveElementVisitor implements Runnable {
  private final TextRange myOriginalRange;
  private final Document myDocument;
  private final PsiDocumentManager myDocumentManager;
  private final PostFormatProcessorHelper myPostProcessorHelper;
  private final PsiElement myContext;
  private final String myNewQuote;

  public HtmlQuotesConverter(@NotNull CodeStyleSettings.QuoteStyle style,
                             @NotNull PsiElement context,
                             @NotNull PostFormatProcessorHelper postFormatProcessorHelper) {
    myPostProcessorHelper = postFormatProcessorHelper;
    Project project = context.getProject();
    PsiFile file = context.getContainingFile();
    myContext = context;
    myOriginalRange = postFormatProcessorHelper.getResultTextRange();
    myDocumentManager = PsiDocumentManager.getInstance(project);
    myDocument = file.getViewProvider().getDocument();
    myNewQuote = switch (style) {
      case Single -> "'";
      case Double -> "\"";
      default -> String.valueOf(0);
    };
  }

  public Document getDocument() {
    return myDocument;
  }

  @Override
  public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
    //use original range to check because while we are modifying document, element ranges returned from getTextRange() are not updated.
    if (myOriginalRange.contains(value.getTextRange())) {
      PsiElement child = value.getFirstChild();
      if (child != null &&
          !containsQuoteChars(value) // For now, we skip values containing quotes to be inserted/replaced
      ) {
        if (child.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
          PsiElement lastChild = value.getLastChild();
          if (lastChild != null && lastChild.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
            CharSequence delimiterChars = child.getNode().getChars();
            if (delimiterChars.length() == 1 && !StringUtil.equals(delimiterChars, myNewQuote)) {
              int startOffset = value.getTextRange().getStartOffset();
              int endOffset = value.getTextRange().getEndOffset();
              replaceString(startOffset, startOffset + 1, myNewQuote);
              replaceString(endOffset - 1, endOffset, myNewQuote);
            }
          }
        }
        else if (child.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
                 && child == value.getLastChild()) {
          insertString(child.getTextRange().getStartOffset(), myNewQuote);
          insertString(child.getTextRange().getEndOffset(), myNewQuote);
        }
      }
    }
  }

  private void replaceString(int start, int end, String newValue) {
    final int mappedStart = myPostProcessorHelper.mapOffset(start);
    final int mappedEnd = myPostProcessorHelper.mapOffset(end);
    myDocument.replaceString(mappedStart, mappedEnd, newValue);
    myPostProcessorHelper.updateResultRange(end - start, newValue.length());
  }

  private void insertString(int offset, String value) {
    final int mappedOffset = myPostProcessorHelper.mapOffset(offset);
    myDocument.insertString(mappedOffset, value);
    myPostProcessorHelper.updateResultRange(0, value.length());
  }

  private boolean containsQuoteChars(@NotNull XmlAttributeValue value) {
    for (PsiElement child = value.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!isDelimiter(child.getNode().getElementType())
          && StringUtil.contains(child.getNode().getChars(), myNewQuote)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isDelimiter(@NotNull IElementType elementType) {
    return elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER ||
           elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;
  }

  @Override
  public void run() {
    if (myDocument == null) return;
    runSimple();
    myDocumentManager.commitDocument(myDocument);
  }

  public void runSimple() {
    if (myDocument == null) return;
    myDocumentManager.doPostponedOperationsAndUnblockDocument(myDocument);
    myContext.accept(this);
  }

  public static void runOnElement(@NotNull CodeStyleSettings.QuoteStyle quoteStyle, @NotNull PsiElement element) {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(element.getContainingFile()).getCommonSettings(HTMLLanguage.INSTANCE);
    PostFormatProcessorHelper postFormatProcessorHelper = new PostFormatProcessorHelper(settings);
    postFormatProcessorHelper.setResultTextRange(element.getTextRange());
    new HtmlQuotesConverter(quoteStyle, element, postFormatProcessorHelper).runSimple();
  }
}
