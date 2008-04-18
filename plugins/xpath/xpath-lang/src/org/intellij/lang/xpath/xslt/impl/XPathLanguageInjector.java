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

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.xslt.XsltSupport;

import java.util.List;

class XPathLanguageInjector implements LanguageInjector {
    private static final Key<Pair<String, TextRange[]>> CACHED_FILES = Key.create("CACHED_FILES");
    private static final TextRange[] EMPTY_ARRAY = new TextRange[0];

    private final ParserDefinition myParserDefinition;

    public XPathLanguageInjector() {
        myParserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage());
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
    public TextRange[] getInjectionRanges(final XmlAttribute attribute) {
        if (!XsltSupport.isXsltFile(attribute.getContainingFile())) return EMPTY_ARRAY;
        if (!XsltSupport.isXPathAttribute(attribute)) return EMPTY_ARRAY;

        final TextRange[] cachedFiles = getCachedRanges(attribute);
        if (cachedFiles != null) {
            return cachedFiles;
        }

        final String value = attribute.getDisplayValue();
        if (value == null) return EMPTY_ARRAY;

        final TextRange[] ranges;
        if (XsltSupport.mayBeAVT(attribute)) {
            final List<TextRange> avtRanges = new SmartList<TextRange>();

            int i, j = 0;
            final Lexer lexer = myParserDefinition.createLexer(attribute.getProject());
            while ((i = XsltSupport.getAVTOffset(value, j)) != -1) {
                // "A right curly brace inside a Literal in an expression is not recognized as terminating the expression."
                lexer.start(value, i, value.length(), 0);
                j = -1;
                while (lexer.getTokenType() != null) {
                    if (lexer.getTokenType() == XPathTokenTypes.RBRACE) {
                        j = lexer.getTokenStart();
                        break;
                    }
                    lexer.advance();
                }

                if (j != -1) {
                    avtRanges.add(AVTRange.create(attribute, i, j + 1, true));
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

    public synchronized void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
        if (host instanceof XmlAttributeValue) {
            final PsiElement parent = host.getParent();
            if (parent instanceof XmlAttribute) {
                final XmlAttribute attribute = (XmlAttribute)parent;
                final TextRange[] ranges = getInjectionRanges(attribute);
                for (TextRange range : ranges) {
                    // workaround for http://www.jetbrains.net/jira/browse/IDEA-10096
                    if (range instanceof AVTRange) {
                        if (((AVTRange)range).myComplete) {
                            injectionPlacesRegistrar.addPlace(XPathFileType.XPATH.getLanguage(), range.shiftRight(2).grown(-2), "", "");
                        } else {
                            // we need to keep the "'}' expected" parse error
                            injectionPlacesRegistrar.addPlace(XPathFileType.XPATH.getLanguage(), range.shiftRight(2).grown(-1), "{", "");
                        }
                    } else {
                        injectionPlacesRegistrar.addPlace(XPathFileType.XPATH.getLanguage(), range, "", "");
                    }
                }
            }
        }
    }
}
