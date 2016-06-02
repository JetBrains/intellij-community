/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.html;


import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlQuotesFormatPreprocessor implements PreFormatProcessor {
  @NotNull
  @Override
  public TextRange process(@NotNull ASTNode node, @NotNull TextRange range) {
    PsiElement psiElement = node.getPsi();
    if (psiElement != null &&
        psiElement.isValid() &&
        psiElement.getLanguage().is(HTMLLanguage.INSTANCE)) {
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(psiElement.getProject());
      CodeStyleSettings.QuoteStyle quoteStyle = settings.HTML_QUOTE_STYLE;
      if (quoteStyle != CodeStyleSettings.QuoteStyle.None && settings.HTML_ENFORCE_QUOTES) {
        HtmlQuotesConverter converter = new HtmlQuotesConverter(quoteStyle, psiElement, range);
        Document document = converter.getDocument();
        if (document != null) {
          DocumentUtil.executeInBulk(document, true, converter);
        }
        return converter.getTextRange();
      }
    }
    return range;
  }


  private static class HtmlQuotesConverter extends XmlRecursiveElementVisitor implements Runnable {
    private TextRange myTextRange;
    private final Document myDocument;
    private final PsiDocumentManager myDocumentManager;
    private final PsiElement myElement;
    private int myDelta = 0;
    private final char myQuoteChar;

    private HtmlQuotesConverter(CodeStyleSettings.QuoteStyle style,
                                @NotNull PsiElement element,
                                @NotNull TextRange textRange) {
      Project project = element.getProject();
      PsiFile file = element.getContainingFile();
      myElement = element;
      myTextRange = new TextRange(textRange.getStartOffset(), textRange.getEndOffset());
      myDocumentManager = PsiDocumentManager.getInstance(project);
      myDocument = myDocumentManager.getDocument(file);
      switch (style) {
        case Single:
          myQuoteChar = '\'';
          break;
        case Double:
          myQuoteChar = '"';
          break;
        default:
          myQuoteChar = 0;
      }
    }

    public TextRange getTextRange() {
      return myTextRange.grown(myDelta);
    }

    public Document getDocument() {
      return myDocument;
    }

    @Override
    public void visitXmlAttributeValue(XmlAttributeValue value) {
      if (myTextRange.contains(value.getTextRange())) {
        PsiElement child = value.getFirstChild();
        if (child != null &&
            !containsQuoteChars(value) // For now we skip values containing quotes to be inserted/replaced
          ) {
          String newValue = null;
          if (child.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
            PsiElement lastChild = value.getLastChild();
            if (lastChild != null && lastChild.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
              CharSequence delimiterChars = child.getNode().getChars();
              if (delimiterChars.length() == 1) {
                char existingQuote = delimiterChars.charAt(0);
                if (existingQuote != myQuoteChar) {
                  newValue = convertQuotes(value);
                }
              }
            }
          }
          else if (child.getNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
                   && child == value.getLastChild()) {
            newValue = surroundWithQuotes(value);
          }
          if (newValue != null) {
            int startOffset = value.getTextRange().getStartOffset() + myDelta;
            int endOffset = value.getTextRange().getEndOffset() + myDelta;
            myDocument.replaceString(startOffset, endOffset, newValue);
            myDelta += newValue.length() - value.getTextLength();
          }
        }
      }
    }

    @Nullable
    private String convertQuotes(@NotNull XmlAttributeValue value) {
      String currValue = value.getNode().getChars().toString();
      if (currValue.length() >= 2) {
        return myQuoteChar + currValue.substring(1, currValue.length() - 1) + myQuoteChar;
      }
      return null;
    }

    @NotNull
    private String surroundWithQuotes(@NotNull XmlAttributeValue value) {
      String currValue = value.getNode().getChars().toString();
      return myQuoteChar + currValue + myQuoteChar;
    }

    private boolean containsQuoteChars(@NotNull XmlAttributeValue value) {
      for (PsiElement child = value.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (!isDelimiter(child.getNode().getElementType())) {
          CharSequence valueChars = child.getNode().getChars();
          for (int i = 0; i < valueChars.length(); i ++) {
            if (valueChars.charAt(i) == myQuoteChar) return true;
          }
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
      if (myDocument != null) {
        myDocumentManager.doPostponedOperationsAndUnblockDocument(myDocument);
        myElement.accept(this);
        myDocumentManager.commitDocument(myDocument);
      }
    }
  }
}
