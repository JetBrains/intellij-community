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
import com.intellij.psi.tree.TokenSet;

public interface MarkdownTokenTypeSets extends MarkdownElementTypes {
  TokenSet WHITE_SPACES = TokenSet.create(MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.EOL, TokenType.WHITE_SPACE);

  TokenSet HEADER_MARKERS = TokenSet.create(
    MarkdownTokenTypes.ATX_HEADER,
    MarkdownTokenTypes.SETEXT_1,
    MarkdownTokenTypes.SETEXT_2);
  TokenSet HEADER_LEVEL_1_SET = TokenSet.create(ATX_1, SETEXT_1);
  TokenSet HEADER_LEVEL_2_SET = TokenSet.create(ATX_2, SETEXT_2);
  TokenSet HEADER_LEVEL_3_SET = TokenSet.create(ATX_3);
  TokenSet HEADER_LEVEL_4_SET = TokenSet.create(ATX_4);
  TokenSet HEADER_LEVEL_5_SET = TokenSet.create(ATX_5);
  TokenSet HEADER_LEVEL_6_SET = TokenSet.create(ATX_6);
  TokenSet HEADERS = TokenSet.orSet(HEADER_LEVEL_1_SET,
                                    HEADER_LEVEL_2_SET,
                                    HEADER_LEVEL_3_SET,
                                    HEADER_LEVEL_4_SET,
                                    HEADER_LEVEL_5_SET,
                                    HEADER_LEVEL_6_SET);
  TokenSet ATX_HEADERS = TokenSet.create(ATX_1, ATX_2, ATX_3, ATX_4, ATX_5, ATX_6);

  TokenSet REFERENCE_LINK_SET = TokenSet.create(FULL_REFERENCE_LINK, SHORT_REFERENCE_LINK);

  TokenSet CODE_FENCE_ITEMS = TokenSet.create(MarkdownTokenTypes.CODE_FENCE_CONTENT,
                                              MarkdownTokenTypes.CODE_FENCE_START,
                                              MarkdownTokenTypes.CODE_FENCE_END,
                                              MarkdownTokenTypes.FENCE_LANG);

  TokenSet LIST_MARKERS = TokenSet.create(MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER);

  TokenSet LISTS = TokenSet.create(MarkdownElementTypes.ORDERED_LIST, MarkdownElementTypes.UNORDERED_LIST);

  TokenSet INLINE_HOLDING_ELEMENT_TYPES = TokenSet.orSet(HEADERS, TokenSet.create(MarkdownElementTypes.PARAGRAPH,
                                                                                  MarkdownTokenTypes.ATX_CONTENT,
                                                                                  MarkdownTokenTypes.SETEXT_CONTENT,
                                                                                  MarkdownElementTypes.LINK_TEXT));


  TokenSet AUTO_LINKS = TokenSet.create(MarkdownElementTypes.AUTOLINK,
                                        MarkdownTokenTypes.GFM_AUTOLINK,
                                        MarkdownTokenTypes.EMAIL_AUTOLINK);

  TokenSet LINKS = TokenSet.orSet(AUTO_LINKS, TokenSet.create(INLINE_LINK));

  TokenSet INLINE_HOLDING_ELEMENT_PARENTS_TYPES =
    TokenSet.create(MarkdownTokenTypes.ATX_HEADER,
                    MarkdownTokenTypes.SETEXT_1,
                    MarkdownTokenTypes.SETEXT_2);
}
