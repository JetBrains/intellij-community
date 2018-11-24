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
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.psi.RestReference;
import com.jetbrains.rest.psi.RestReferenceTarget;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User : catherine
 */
public class ReferenceCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PsiElement> REFERENCE_PATTERN =
                          psiElement().afterSibling(psiElement().withElementType(RestTokenTypes.EXPLISIT_MARKUP_START));

  public static final PsiElementPattern.Capture<PsiElement> PATTERN =
                          psiElement().andOr(psiElement().withParent(REFERENCE_PATTERN), REFERENCE_PATTERN);

  public ReferenceCompletionContributor() {
    extend(CompletionType.BASIC, PATTERN,
       new CompletionProvider<CompletionParameters>() {
         @Override
         protected void addCompletions(@NotNull CompletionParameters parameters,
                                       ProcessingContext context,
                                       @NotNull CompletionResultSet result) {
           PsiElement original = parameters.getPosition();
           PsiFile file = original.getContainingFile();
           int offset = parameters.getOffset();
           String prefix = getPrefix(offset, file);

           if (prefix.length() > 0) {
            result = result.withPrefixMatcher(prefix);
           }
           RestReference[] elements = PsiTreeUtil.getChildrenOfType(file, RestReference.class);
           RestReferenceTarget[] targets = PsiTreeUtil.getChildrenOfType(file, RestReferenceTarget.class);
           Set<String> names = new HashSet<>();
           if (targets != null) {
             for (RestReferenceTarget t : targets) {
               names.add(t.getReferenceName());
             }
           }

           if (elements != null) {
             for (RestReference e : elements) {
               String name = e.getReferenceText();
               if (!names.contains(name)) {
                 if ((name.startsWith("[") && name.endsWith("]")) || (name.startsWith("|") && name.endsWith("|"))) {
                   result.addElement(LookupElementBuilder.create(name));
                 }
                 else if (name.equals("__")) {
                   result.addElement(LookupElementBuilder.create(name + ":"));
                 }
                 else {
                   name = name.startsWith("_") ? "\\" + name : name;
                   result.addElement(LookupElementBuilder.create("_" + name + ":"));
                 }
               }
             }
           }
         }
       });
  }

  public static String getPrefix(int offset, PsiFile file) {
    if (offset > 0) {
      offset--;
    }
    final String text = file.getText();
    StringBuilder prefixBuilder = new StringBuilder();
    while(offset > 0) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null && element.getNode().getElementType() == RestTokenTypes.EXPLISIT_MARKUP_START)
        break;
      final char charAt = text.charAt(offset);
      prefixBuilder.insert(0, charAt);
      if (charAt == '_' || charAt == '[' || charAt == '|' || charAt == '\n') {
        break;
      }
      offset--;
    }
    return prefixBuilder.toString();
  }
}
