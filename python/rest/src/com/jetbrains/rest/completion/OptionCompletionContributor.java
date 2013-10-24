/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.rest.RestUtil;
import com.jetbrains.rest.psi.RestDirectiveBlock;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User : catherine
 */
public class OptionCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PsiElement> OPTION_PATTERN = psiElement().withParent(RestDirectiveBlock.class);

  public OptionCompletionContributor() {
    extend(CompletionType.BASIC, OPTION_PATTERN,
       new CompletionProvider<CompletionParameters>() {
         @Override
         protected void addCompletions(@NotNull CompletionParameters parameters,
                                       ProcessingContext context,
                                       @NotNull CompletionResultSet result) {

           RestDirectiveBlock original = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), RestDirectiveBlock.class);
           if (original != null) {

             int offset = parameters.getOffset();
             final PsiFile file = parameters.getOriginalFile();
             String prefix = getPrefix(offset, file);

             if (prefix.length() > 0) {
              result = result.withPrefixMatcher(prefix);
             }
             for (String tag : RestUtil.getDirectiveOptions(original.getDirectiveName())) {
               result.addElement(LookupElementBuilder.create(tag + " "));
             }
           }

         }

         private String getPrefix(int offset, PsiFile file) {
           if (offset > 0) {
             offset--;
           }
           final String text = file.getText();
           StringBuilder prefixBuilder = new StringBuilder();
           while(offset > 0 && (Character.isLetterOrDigit(text.charAt(offset)) || text.charAt(offset) == ':')) {
             prefixBuilder.insert(0, text.charAt(offset));
             if (text.charAt(offset) == ':') {
               break;
             }
             offset--;
           }
           return prefixBuilder.toString();
         }
       }
       );
  }
}
