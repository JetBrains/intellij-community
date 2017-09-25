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
package com.intellij.lang.html;


import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

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
        PostFormatProcessorHelper postFormatProcessorHelper = new PostFormatProcessorHelper(settings);
        postFormatProcessorHelper.setResultTextRange(range);
        HtmlQuotesConverter converter = new HtmlQuotesConverter(quoteStyle, psiElement, postFormatProcessorHelper);
        Document document = converter.getDocument();
        if (document != null) {
          DocumentUtil.executeInBulk(document, true, converter);
        }
        return postFormatProcessorHelper.getResultTextRange();
      }
    }
    return range;
  }


  public static class HtmlQuotesConverter extends XmlRecursiveElementVisitor implements Runnable {
    private TextRange myOriginalRange;
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
      myDocument = myDocumentManager.getDocument(file);
      switch (style) {
        case Single:
          myNewQuote = "\'";
          break;
        case Double:
          myNewQuote = "\"";
          break;
        default:
          myNewQuote = String.valueOf(0);
      }
    }

    public Document getDocument() {
      return myDocument;
    }

    @Override
    public void visitXmlAttributeValue(XmlAttributeValue value) {
      //use original range to check because while we are modifying document, element ranges returned from getTextRange() are not updated.
      if (myOriginalRange.contains(value.getTextRange())) {
        PsiElement child = value.getFirstChild();
        if (child != null &&
            !containsQuoteChars(value) // For now we skip values containing quotes to be inserted/replaced
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
      if (myDocument != null) {
        myDocumentManager.doPostponedOperationsAndUnblockDocument(myDocument);
        myContext.accept(this);
        myDocumentManager.commitDocument(myDocument);
      }
    }

    public static void runOnElement(@NotNull CodeStyleSettings.QuoteStyle quoteStyle, @NotNull PsiElement element) {
      PostFormatProcessorHelper postFormatProcessorHelper = new PostFormatProcessorHelper(null);
      postFormatProcessorHelper.setResultTextRange(element.getTextRange());
      new HtmlQuotesConverter(quoteStyle, element, postFormatProcessorHelper).run();
    }
  }
}
