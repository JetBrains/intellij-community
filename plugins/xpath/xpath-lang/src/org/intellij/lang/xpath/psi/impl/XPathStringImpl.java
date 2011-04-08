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
import com.intellij.lang.Language;
import org.intellij.lang.xpath.XPath2Language;
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
    final boolean xpath2 = getContainingFile().getLanguage() == Language.findInstance(XPath2Language.class);
    final String text = getText();
    if (text.startsWith("'")) {
      if (!text.endsWith("'")) {
        return false;
      }
      if (!xpath2 && getValue().indexOf('\'') != getValue().lastIndexOf('\'')) {
        return false;
      }
    } else if (text.startsWith("\"")) {
      if (!text.endsWith("\"")) {
        return false;
      }
      if (!xpath2 && getValue().indexOf('\"') != getValue().lastIndexOf('"')) {
        return false;
      }
    }
    return xpath2 || text.indexOf('\n') == -1 && text.indexOf("\r") == -1;
  }

  public String getValue() {
    final String value = getText().substring(0, getTextLength() - 1);
    if (getContainingFile().getLanguage() == Language.findInstance(XPath2Language.class)) {
      return value.replaceAll("\"\"", "\"").replaceAll("''", "'");
    }
    return value;
  }

  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathString(this);
  }
}