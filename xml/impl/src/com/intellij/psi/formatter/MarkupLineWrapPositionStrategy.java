/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.openapi.editor.LineWrapPositionStrategy;
import com.intellij.openapi.editor.PsiAwareDefaultLineWrapPositionStrategy;
import com.intellij.psi.TokenType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * {@link LineWrapPositionStrategy} for markup languages like XML, HTML etc.
 * 
 * @author Denis Zhdanov
 * @since 5/11/11 7:42 PM
 */
public class MarkupLineWrapPositionStrategy extends PsiAwareDefaultLineWrapPositionStrategy {

  public MarkupLineWrapPositionStrategy() {
    super(true, XmlElementType.XML_TEXT, XmlTokenType.XML_COMMENT_CHARACTERS, TokenType.WHITE_SPACE);
  }
}
