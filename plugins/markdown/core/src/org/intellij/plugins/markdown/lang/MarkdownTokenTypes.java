/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.jetbrains.annotations.NotNull;

import static org.intellij.plugins.markdown.lang.MarkdownElementType.platformType;

public interface MarkdownTokenTypes extends TokenType {

  @NotNull IElementType TEXT = platformType(org.intellij.markdown.MarkdownTokenTypes.TEXT);

  @NotNull IElementType CODE_LINE = platformType(org.intellij.markdown.MarkdownTokenTypes.CODE_LINE);

  @NotNull IElementType ATX_CONTENT = platformType(org.intellij.markdown.MarkdownTokenTypes.ATX_CONTENT);

  @NotNull IElementType SETEXT_CONTENT = platformType(org.intellij.markdown.MarkdownTokenTypes.SETEXT_CONTENT);

  @NotNull IElementType BLOCK_QUOTE = platformType(org.intellij.markdown.MarkdownTokenTypes.BLOCK_QUOTE);

  @NotNull IElementType HTML_BLOCK_CONTENT = platformType(org.intellij.markdown.MarkdownTokenTypes.HTML_BLOCK_CONTENT);

  @NotNull IElementType SINGLE_QUOTE = platformType(org.intellij.markdown.MarkdownTokenTypes.SINGLE_QUOTE);
  @NotNull IElementType DOUBLE_QUOTE = platformType(org.intellij.markdown.MarkdownTokenTypes.DOUBLE_QUOTE);
  @NotNull IElementType LPAREN = platformType(org.intellij.markdown.MarkdownTokenTypes.LPAREN);
  @NotNull IElementType RPAREN = platformType(org.intellij.markdown.MarkdownTokenTypes.RPAREN);
  @NotNull IElementType LBRACKET = platformType(org.intellij.markdown.MarkdownTokenTypes.LBRACKET);
  @NotNull IElementType RBRACKET = platformType(org.intellij.markdown.MarkdownTokenTypes.RBRACKET);
  @NotNull IElementType LT = platformType(org.intellij.markdown.MarkdownTokenTypes.LT);
  @NotNull IElementType GT = platformType(org.intellij.markdown.MarkdownTokenTypes.GT);

  @NotNull IElementType COLON = platformType(org.intellij.markdown.MarkdownTokenTypes.COLON);
  @NotNull IElementType EXCLAMATION_MARK = platformType(org.intellij.markdown.MarkdownTokenTypes.EXCLAMATION_MARK);


  @NotNull IElementType HARD_LINE_BREAK = platformType(org.intellij.markdown.MarkdownTokenTypes.HARD_LINE_BREAK);
  @NotNull IElementType EOL = platformType(org.intellij.markdown.MarkdownTokenTypes.EOL);

  @NotNull IElementType LINK_ID = platformType(org.intellij.markdown.MarkdownTokenTypes.LINK_ID);
  @NotNull IElementType ATX_HEADER = platformType(org.intellij.markdown.MarkdownTokenTypes.ATX_HEADER);
  @NotNull IElementType EMPH = platformType(org.intellij.markdown.MarkdownTokenTypes.EMPH);
  @NotNull IElementType TILDE = platformType(GFMTokenTypes.TILDE);

  @NotNull IElementType BACKTICK = platformType(org.intellij.markdown.MarkdownTokenTypes.BACKTICK);
  @NotNull IElementType ESCAPED_BACKTICKS = platformType(org.intellij.markdown.MarkdownTokenTypes.ESCAPED_BACKTICKS);

  @NotNull IElementType DOLLAR = platformType(GFMTokenTypes.DOLLAR);

  @NotNull IElementType LIST_BULLET = platformType(org.intellij.markdown.MarkdownTokenTypes.LIST_BULLET);
  @NotNull IElementType URL = platformType(org.intellij.markdown.MarkdownTokenTypes.URL);
  @NotNull IElementType HORIZONTAL_RULE = platformType(org.intellij.markdown.MarkdownTokenTypes.HORIZONTAL_RULE);
  @NotNull IElementType TABLE_SEPARATOR = platformType(GFMTokenTypes.TABLE_SEPARATOR);
  @NotNull IElementType SETEXT_1 = platformType(org.intellij.markdown.MarkdownTokenTypes.SETEXT_1);
  @NotNull IElementType SETEXT_2 = platformType(org.intellij.markdown.MarkdownTokenTypes.SETEXT_2);
  @NotNull IElementType LIST_NUMBER = platformType(org.intellij.markdown.MarkdownTokenTypes.LIST_NUMBER);
  @NotNull IElementType FENCE_LANG = platformType(org.intellij.markdown.MarkdownTokenTypes.FENCE_LANG);
  @NotNull IElementType CODE_FENCE_START = platformType(org.intellij.markdown.MarkdownTokenTypes.CODE_FENCE_START);
  @NotNull IElementType CODE_FENCE_END = platformType(org.intellij.markdown.MarkdownTokenTypes.CODE_FENCE_END);
  @NotNull IElementType CODE_FENCE_CONTENT = platformType(org.intellij.markdown.MarkdownTokenTypes.CODE_FENCE_CONTENT);
  @NotNull IElementType LINK_TITLE = platformType(org.intellij.markdown.MarkdownTokenTypes.LINK_TITLE);

  @NotNull IElementType GFM_AUTOLINK = platformType(GFMTokenTypes.GFM_AUTOLINK);
  /**
   * @see MarkdownElementTypes#AUTOLINK
   */
  @NotNull IElementType AUTOLINK = platformType(org.intellij.markdown.MarkdownTokenTypes.AUTOLINK);
  @NotNull IElementType EMAIL_AUTOLINK = platformType(org.intellij.markdown.MarkdownTokenTypes.EMAIL_AUTOLINK);
  @NotNull IElementType HTML_TAG = platformType(org.intellij.markdown.MarkdownTokenTypes.HTML_TAG);

  @NotNull IElementType CHECK_BOX = platformType(GFMTokenTypes.CHECK_BOX);

  @NotNull IElementType ALERT_TITLE = platformType(GFMTokenTypes.ALERT_TITLE);

  @NotNull IElementType BAD_CHARACTER = platformType(org.intellij.markdown.MarkdownTokenTypes.BAD_CHARACTER);
  @NotNull IElementType WHITE_SPACE = platformType(org.intellij.markdown.MarkdownTokenTypes.WHITE_SPACE);
}
