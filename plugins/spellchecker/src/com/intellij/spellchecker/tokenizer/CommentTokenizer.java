/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiComment;
import com.intellij.spellchecker.inspections.CommentSplitter;
import org.jetbrains.annotations.NotNull;

public class CommentTokenizer extends Tokenizer<PsiComment> {

  @Override
  public void tokenize(@NotNull PsiComment element, TokenConsumer consumer) {
    // doccomment chameleon expands as PsiComment inside PsiComment, avoid duplication
    if (element.getParent() instanceof PsiComment) return;
    consumer.consumeToken(element, CommentSplitter.getInstance());
  }
}
