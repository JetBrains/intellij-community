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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.extensions.CaptureExtKt;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


public final class PyParameterCompletionContributor extends CompletionContributor implements DumbAware {
  public PyParameterCompletionContributor() {
    extend(CompletionType.BASIC,
           CaptureExtKt.inParameterList(psiElement()).afterLeaf("*"),
           new ParameterCompletionProvider("args"));
    extend(CompletionType.BASIC,
           CaptureExtKt.inParameterList(psiElement()).afterLeaf("**"),
           new ParameterCompletionProvider("kwargs"));
  }

  private static final class ParameterCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final String myName;

    private ParameterCompletionProvider(String name) {
      myName = name;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create(myName).withIcon(AllIcons.Nodes.Parameter));
    }
  }
}
