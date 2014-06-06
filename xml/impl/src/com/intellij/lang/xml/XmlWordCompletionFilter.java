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

/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.lang.DefaultWordCompletionFilter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;

public class XmlWordCompletionFilter extends DefaultWordCompletionFilter {
  private final static TokenSet ENABLED_TOKENS = TokenSet.create(XmlElementType.XML_CDATA,
                                                                 XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                                                 XmlTokenType.XML_DATA_CHARACTERS);
  @Override
  public boolean isWordCompletionEnabledIn(final IElementType element) {
    return super.isWordCompletionEnabledIn(element) || ENABLED_TOKENS.contains(element);
  }
}