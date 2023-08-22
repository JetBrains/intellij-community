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
package com.intellij.xml.impl;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.XmlAwareBraceMatcher;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class XmlBraceMatcher implements XmlAwareBraceMatcher {
  private static final int XML_TAG_TOKEN_GROUP = 1;
  private static final int XML_VALUE_DELIMITER_GROUP = 2;

  private static final BidirectionalMap<IElementType, IElementType> PAIRING_TOKENS = new BidirectionalMap<>();

  static {
    PAIRING_TOKENS.put(XmlTokenType.XML_TAG_END, XmlTokenType.XML_START_TAG_START);
    PAIRING_TOKENS.put(XmlTokenType.XML_CDATA_START, XmlTokenType.XML_CDATA_END);
    PAIRING_TOKENS.put(XmlTokenType.XML_EMPTY_ELEMENT_END, XmlTokenType.XML_START_TAG_START);
    PAIRING_TOKENS.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER);
  }

  @Override
  public int getBraceTokenGroupId(@NotNull IElementType tokenType) {
    final Language l = tokenType.getLanguage();
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(l);

    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if (pair.getLeftBraceType() == tokenType || pair.getRightBraceType() == tokenType ) {
          return l.hashCode();
        }
      }
    }
    if (tokenType instanceof IXmlLeafElementType) {
      return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER || tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
             ? XML_VALUE_DELIMITER_GROUP
             : XML_TAG_TOKEN_GROUP;
    }
    else{
      return BraceMatchingUtil.UNDEFINED_TOKEN_GROUP;
    }
  }

  @Override
  public boolean isLBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if (pair.getLeftBraceType() == tokenType) return true;
      }
    }
    return tokenType == XmlTokenType.XML_START_TAG_START ||
           tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER ||
           tokenType == XmlTokenType.XML_CDATA_START;
  }

  @Override
  public boolean isRBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if (pair.getRightBraceType() == tokenType) return true;
      }
    }

    if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
        tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER ||
        tokenType == XmlTokenType.XML_CDATA_END)
    {
      return true;
    }
    else if (tokenType == XmlTokenType.XML_TAG_END) {
      final boolean result = findEndTagStart(iterator);

      if (isFileTypeWithSingleHtmlTags(fileType)) {
        final String tagName = getTagName(fileText, iterator);

        if (tagName != null && HtmlUtil.isSingleHtmlTag(tagName, false)) {
          return !result;
        }
      }

      return result;
    }
    else {
      return false;
    }
  }

  protected boolean isFileTypeWithSingleHtmlTags(final FileType fileType) {
    return fileType == HtmlFileType.INSTANCE;
  }

  @Override
  public boolean isPairBraces(@NotNull IElementType tokenType1, @NotNull IElementType tokenType2) {
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType1.getLanguage());
    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if (pair.getLeftBraceType() == tokenType1 ) return pair.getRightBraceType() == tokenType2;
        if (pair.getRightBraceType() == tokenType1 ) return pair.getLeftBraceType() == tokenType2;
      }
    }
    if (tokenType2.equals(PAIRING_TOKENS.get(tokenType1))) return true;
    List<IElementType> keys = PAIRING_TOKENS.getKeysByValue(tokenType1);
    return keys != null && keys.contains(tokenType2);
  }

  @Override
  public boolean isStructuralBrace(@NotNull HighlighterIterator iterator, @NotNull CharSequence text, @NotNull FileType fileType) {
    IElementType tokenType = iterator.getTokenType();

    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if ((pair.getLeftBraceType() == tokenType || pair.getRightBraceType() == tokenType) &&
            pair.isStructural()) return true;
      }
    }
    return isXmlStructuralBrace(tokenType);
  }

  protected boolean isXmlStructuralBrace(IElementType tokenType) {
    return tokenType == XmlTokenType.XML_START_TAG_START ||
           tokenType == XmlTokenType.XML_TAG_END ||
           tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    return true;
  }

  @Override
  public boolean isStrictTagMatching(final @NotNull FileType fileType, final int braceGroupId) {
    if (braceGroupId == XML_TAG_TOKEN_GROUP) {
      // Other xml languages may have nonbalanced tag names
      return isStrictTagMatchingForFileType(fileType);
    }
    return false;
  }

  protected boolean isStrictTagMatchingForFileType(final FileType fileType) {
    return fileType == XmlFileType.INSTANCE ||
           fileType == XHtmlFileType.INSTANCE;
  }

  @Override
  public boolean areTagsCaseSensitive(final @NotNull FileType fileType, final int braceGroupId) {
    return braceGroupId == XML_TAG_TOKEN_GROUP && fileType == XmlFileType.INSTANCE;
  }

  private static boolean findEndTagStart(HighlighterIterator iterator) {
    IElementType tokenType = iterator.getTokenType();
    int balance = 0;
    int count = 0;
    while(balance >= 0){
      iterator.retreat();
      count++;
      if (iterator.atEnd()) break;
      tokenType = iterator.getTokenType();
      if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END){
        balance++;
      }
      else if (tokenType == XmlTokenType.XML_END_TAG_START || tokenType == XmlTokenType.XML_START_TAG_START){
        balance--;
      }
    }
    while(count-- > 0) iterator.advance();
    return tokenType == XmlTokenType.XML_END_TAG_START;
  }

  @Override
  public String getTagName(@NotNull CharSequence fileText, @NotNull HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();
    String name = null;
    if (tokenType == XmlTokenType.XML_START_TAG_START) {
      iterator.advance();
      IElementType tokenType1 = iterator.atEnd() ? null : iterator.getTokenType();

      boolean wasWhiteSpace = false;
      if (isWhitespace(tokenType1)) {
        wasWhiteSpace = true;
        iterator.advance();
        tokenType1 = iterator.atEnd() ? null : iterator.getTokenType();
      }

      if (tokenType1 == XmlTokenType.XML_TAG_NAME ||
          tokenType1 == XmlTokenType.XML_NAME
         ) {
        name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
      }

      if (wasWhiteSpace) iterator.retreat();
      iterator.retreat();
    }
    else if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
      int balance = 0;
      int count = 0;
      IElementType tokenType1 = iterator.getTokenType();
      while (balance >=0) {
        iterator.retreat();
        count++;
        if (iterator.atEnd()) break;
        tokenType1 = iterator.getTokenType();

        if (tokenType1 == XmlTokenType.XML_TAG_END || tokenType1 == XmlTokenType.XML_EMPTY_ELEMENT_END) {
          balance++;
        }
        else if (tokenType1 == XmlTokenType.XML_TAG_NAME) {
          balance--;
        }
      }
      if (tokenType1 == XmlTokenType.XML_TAG_NAME) {
        name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
      }
      while (count-- > 0) iterator.advance();
    }

    return name;
  }

  protected boolean isWhitespace(final IElementType tokenType1) {
    return tokenType1 == TokenType.WHITE_SPACE;
  }

  @Override
  public IElementType getOppositeBraceTokenType(@NotNull final IElementType type) {
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(type.getLanguage());
    if (matcher != null) {
      BracePair[] pairs = matcher.getPairs();
      for (BracePair pair : pairs) {
        if (pair.getLeftBraceType() == type ) return pair.getRightBraceType();
        if (pair.getRightBraceType() == type ) return pair.getLeftBraceType();
      }
    }
    return null;
  }

  @Override
  public int getCodeConstructStart(final @NotNull PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
