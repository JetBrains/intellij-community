/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.psi.XPathElementVisitor;
import org.intellij.lang.xpath.psi.XPathString;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;

public class XPathStringImpl extends XPathElementImpl implements XPathString {
  public XPathStringImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  public XPathType getType() {
    return XPathType.STRING;
  }

  public boolean isWellFormed() {
    final String text = getUnescapedText();
    final char quoteChar = getQuoteChar();
    if (!text.endsWith(String.valueOf(quoteChar)) || text.indexOf(quoteChar) == text.lastIndexOf(quoteChar)) {
      return false;
    }

    if (getXPathVersion() == XPathVersion.V2) {
      final String value = getStringBetweenQuotes();
      final String unescaped = unescape(quoteChar, value);
      return escape(quoteChar, unescaped).equals(value);
    } else {
      if (getValue().indexOf(quoteChar) != -1) {
        return false;
      }
    }

    return !textContains('\n') && !textContains('\r');
  }

  public String getValue() {
    final String value = getStringBetweenQuotes();
    if (getXPathVersion() == XPathVersion.V2) {
      return unescape(getQuoteChar(), value);
    }
    return value;
  }

  private String getStringBetweenQuotes() {
    final String text = getUnescapedText();
    if (text.endsWith(String.valueOf(getQuoteChar())) && text.length() > 1) {
      return text.substring(1, text.length() - 1);
    } else {
      return text.substring(1);
    }
  }

  private char getQuoteChar() {
    return getUnescapedText().charAt(0);
  }

  private static String unescape(char quote, String value) {
    final String singleQuote = String.valueOf(quote);
    final String escapedQuote = singleQuote + quote;
    return value.replaceAll(escapedQuote, singleQuote);
  }

  private static String escape(char quote, String value) {
    final String singleQuote = String.valueOf(quote);
    final String escapedQuote = singleQuote + quote;
    return value.replaceAll(singleQuote, escapedQuote);
  }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathString(this);
  }
}