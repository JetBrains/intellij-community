/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Weighs down items starting with two underscores.
 * <br/>
 * User: dcheryasov
 */
public class PythonCompletionWeigher extends CompletionWeigher {

  public static final int PRIORITY_WEIGH = 5;

  @NonNls private static final String SINGLE_UNDER = "_";
  @NonNls private static final String DOUBLE_UNDER = "__";

  private static final int ELEMENT_TYPE = 10;
  private static final int PRIVATE_API = 100;
  private static final int UNDERSCORE_IN_NAME = 1000;
  private static final int LOCATION = 10000;
  private static final int FALLBACK_WEIGH = -100000;

  @Override
  public Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
      return 0;
    }

    final String name = element.getLookupString();
    final LookupElementPresentation presentation = LookupElementPresentation.renderElement(element);
    // move dict keys to the top
    if ("dict key".equals(presentation.getTypeText())) {
      return element.getLookupString().length();
    }

    PsiElement psiElement = element.getPsiElement();
    PsiFile file = location.getCompletionParameters().getOriginalFile();
    if (psiElement != null) {
      int weigh = 0;
      if (psiElement instanceof PyFile) {
        weigh -= ELEMENT_TYPE;
      }
      else if (psiElement instanceof PsiDirectory) {
        weigh -= ELEMENT_TYPE * 2;
      }

      Optional<QualifiedName> importPathMaybe = Optional.ofNullable(psiElement.getContainingFile())
        .map(PsiFile::getVirtualFile)
        .map(vfile -> QualifiedNameFinder.findShortestImportableQName(file, vfile));
      if (importPathMaybe.isPresent()) {
        QualifiedName importPath = importPathMaybe.get();
        int privateApiModifier = (int) StreamEx.of(importPath.getComponents())
          .filter(qName -> qName.startsWith(SINGLE_UNDER))
          .count() * PRIVATE_API;
        if (PyStdlibUtil.isInStdLib(importPath.getFirstComponent())) {
          weigh -= LOCATION;
        }
        else if (ModuleUtilCore.findModuleForFile(psiElement.getContainingFile()) == null) {
          weigh -= LOCATION * 2;
        }
        weigh = weigh - privateApiModifier - importPath.getComponentCount();
      }

      if (name.startsWith(DOUBLE_UNDER)) {
        if (name.endsWith(DOUBLE_UNDER)) {
          weigh -= UNDERSCORE_IN_NAME * 3; // __foo__ is lowest
        }
        else {
          weigh -= UNDERSCORE_IN_NAME * 2; // __foo is lower than normal
        }
      }
      else if (name.startsWith(SINGLE_UNDER)) {
        weigh -= UNDERSCORE_IN_NAME;
      }
      return weigh;
    }
    return FALLBACK_WEIGH;
  }
}
