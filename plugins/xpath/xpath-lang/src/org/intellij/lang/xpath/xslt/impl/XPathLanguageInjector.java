/*
 * Copyright 2007 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.SmartList;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class XPathLanguageInjector implements MultiHostInjector {
    private static final Key<Pair<String, TextRange[]>> CACHED_FILES = Key.create("CACHED_FILES");
    private static final TextRange[] EMPTY_ARRAY = new TextRange[0];

    public XPathLanguageInjector() {
    }

    @Nullable
    private static TextRange[] getCachedRanges(XmlAttribute attribute) {
        Pair<String, TextRange[]> pair;
        if ((pair = attribute.getUserData(CACHED_FILES)) != null) {
            if (!attribute.getValue().equals(pair.getFirst())) {
                attribute.putUserData(CACHED_FILES, null);
                return null;
            }
        } else {
            return null;
        }
        return pair.getSecond();
    }

    static final class AVTRange extends TextRange {
        final boolean myComplete;

        private AVTRange(int startOffset, int endOffset, boolean iscomplete) {
            super(startOffset, endOffset);
            myComplete = iscomplete;
        }

        public static AVTRange create(XmlAttribute attribute, int startOffset, int endOffset, boolean iscomplete) {
            return new AVTRange(attribute.displayToPhysical(startOffset), attribute.displayToPhysical(endOffset), iscomplete);
        }
    }

    @NotNull
    private synchronized TextRange[] getInjectionRanges(final XmlAttribute attribute, XsltChecker.LanguageLevel languageLevel) {
      final TextRange[] cachedFiles = getCachedRanges(attribute);
      if (cachedFiles != null) {
        return cachedFiles;
      }

      final String value = attribute.getDisplayValue();
      if (value == null) return EMPTY_ARRAY;

      final TextRange[] ranges;
      if (XsltSupport.mayBeAVT(attribute)) {
        final List<TextRange> avtRanges = new SmartList<>();

        int i;
        int j = 0;
        Lexer lexer = null;
        while ((i = XsltSupport.getAVTOffset(value, j)) != -1) {
          if (lexer == null) {
            lexer = LanguageParserDefinitions.INSTANCE.forLanguage(languageLevel.getXPathVersion().getLanguage())
              .createLexer(attribute.getProject());
          }

          // "A right curly brace inside a Literal in an expression is not recognized as terminating the expression."
          lexer.start(value, i, value.length());
          j = -1;
          while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == XPathTokenTypes.RBRACE) {
              j = lexer.getTokenStart();
              break;
            }
            lexer.advance();
          }

          if (j != -1) {
            avtRanges.add(AVTRange.create(attribute, i, j + 1, j > i + 1));
          } else {
            // missing '}' error will be flagged by xpath parser
            avtRanges.add(AVTRange.create(attribute, i, value.length(), false));
            break;
          }
        }

        if (avtRanges.size() > 0) {
          ranges = avtRanges.toArray(new TextRange[avtRanges.size()]);
        } else {
          ranges = EMPTY_ARRAY;
        }
      } else {
        ranges = new TextRange[]{ attribute.getValueTextRange() };
      }

      attribute.putUserData(CACHED_FILES, Pair.create(attribute.getValue(), ranges));

      return ranges;
    }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(XmlAttribute.class);
  }

  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final XmlAttribute attribute = (XmlAttribute)context;
    if (!XsltSupport.isXPathAttribute(attribute)) return;

    XmlAttributeValueImpl value = (XmlAttributeValueImpl)attribute.getValueElement();
    if (value == null) return;
    ASTNode type = value.findChildByType(XmlElementType.XML_ENTITY_REF);
    if (type != null) return; // workaround for inability to inject into text with entity refs (e.g. IDEA-72972) TODO: fix it

    final XsltChecker.LanguageLevel languageLevel = XsltSupport.getXsltLanguageLevel(attribute.getContainingFile());
    final TextRange[] ranges = getInjectionRanges(attribute, languageLevel);
    for (TextRange range : ranges) {
      // workaround for http://www.jetbrains.net/jira/browse/IDEA-10096
      TextRange rangeInsideHost;
      String prefix;
      if (range instanceof AVTRange) {
        if (((AVTRange)range).myComplete) {
          rangeInsideHost = range.shiftRight(2).grown(-2);
          prefix = "";
        }
        else {
          // we need to keep the "'}' expected" parse error
          rangeInsideHost = range.shiftRight(2).grown(-1);
          prefix = "{";
        }
      }
      else {
        rangeInsideHost = range;
        prefix = "";
      }
      if (value.getTextRange().contains(rangeInsideHost.shiftRight(value.getTextRange().getStartOffset()))) {
        registrar.startInjecting(languageLevel.getXPathVersion().getLanguage())
                .addPlace(prefix, "", value, rangeInsideHost)
                .doneInjecting();
      }
    }
  }
}
