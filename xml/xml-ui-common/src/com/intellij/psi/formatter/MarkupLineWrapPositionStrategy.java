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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.TokenType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LineWrapPositionStrategy} for markup languages like XML, HTML etc.
 */
public class MarkupLineWrapPositionStrategy extends PsiAwareDefaultLineWrapPositionStrategy {

  private final DefaultLineWrapPositionStrategy myDefaultStrategy = new DefaultLineWrapPositionStrategy();

  public MarkupLineWrapPositionStrategy() {
    super(true, XmlElementType.XML_TEXT, XmlElementType.HTML_RAW_TEXT, XmlTokenType.XML_COMMENT_CHARACTERS, TokenType.WHITE_SPACE);

    myDefaultStrategy.addRule(new GenericLineWrapPositionStrategy.Rule('<', GenericLineWrapPositionStrategy.WrapCondition.BEFORE));
    myDefaultStrategy.addRule(new GenericLineWrapPositionStrategy.Rule('/', GenericLineWrapPositionStrategy.WrapCondition.AFTER,
                                                                       GenericLineWrapPositionStrategy.Rule.DEFAULT_WEIGHT - 2));
  }

  @Override
  public int calculateWrapPosition(@NotNull Document document,
                                   @Nullable Project project,
                                   int startOffset,
                                   int endOffset,
                                   int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset,
                                   boolean isSoftWrap) {
    if (isSoftWrap) {
      return myDefaultStrategy.calculateWrapPosition(
        document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, isSoftWrap);
    }
    else {
      return super.calculateWrapPosition(
        document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, isSoftWrap);
    }
  }
}
