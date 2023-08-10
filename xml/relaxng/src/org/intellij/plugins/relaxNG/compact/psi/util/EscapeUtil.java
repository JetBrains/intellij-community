/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.jetbrains.annotations.NotNull;

public final class EscapeUtil {
  public static String unescapeText(@NotNull PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null) {
      return unescapeText(node);
    } else {
      return element.getText();
    }
  }

  public static String unescapeText(@NotNull ASTNode node) {
    final String text = node.getText();

    // TODO: unescape \x{xx} sequences

    if (node.getElementType() == RncTokenTypes.ESCAPED_IDENTIFIER) {
      assert text.charAt(0) == '\\';
      return text.length() > 1 ? text.substring(1) : "";
    } else {
      return text;
    }
  }

  public static String parseLiteralValue(ASTNode ns) {
    return unquote(unescapeText(ns));
  }

  private static String unquote(String s) {
    if (s.length() >= 6 && s.charAt(0) == s.charAt(1)) {
      s = s.replace('\u0000', '\n');
      return s.substring(3, s.length() - 3);
    } else {
      return s.substring(1, s.length() - 1);
    }
  }
}
