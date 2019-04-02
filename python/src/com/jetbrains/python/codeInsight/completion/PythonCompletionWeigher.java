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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * Weighs down items starting with two underscores.
 * <br/>
 * User: dcheryasov
 */
public class PythonCompletionWeigher extends CompletionWeigher {

  public static final int PRIORITY_WEIGHT = 5;

  @NonNls private static final String SINGLE_UNDER = "_";
  @NonNls private static final String DOUBLE_UNDER = "__";

  private static final int ELEMENT_TYPE = 10;
  private static final int LOCATION = 100;
  private static final int PRIVATE_API = 1000;
  private static final int LOCATION_ALREADY_IMPORTED = 10000;
  private static final int UNDERSCORE_IN_NAME = 100000;
  private static final int FALLBACK_WEIGHT = -100000;

  @Override
  public Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    if (!PsiUtilCore.findLanguageFromElement(location.getCompletionParameters().getPosition()).isKindOf(PythonLanguage.getInstance())) {
      return FALLBACK_WEIGHT;
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
      PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile == file) return PRIORITY_WEIGHT;
      VirtualFile vfile = null;
      Sdk sdk = null;
      int weight = 0;

      if (psiElement instanceof PsiDirectory) {
        vfile = ((PsiDirectory)psiElement).getVirtualFile();
        sdk = PythonSdkType.findPythonSdk(psiElement);
        weight -= ELEMENT_TYPE * 2;
      }
      else if (containingFile != null) {
        vfile = containingFile.getVirtualFile();
        sdk = PythonSdkType.findPythonSdk(containingFile);
        if (psiElement instanceof PyFile) {
          weight -= ELEMENT_TYPE;
        }
      }

      if (vfile != null) {
        if (PythonSdkType.isStdLib(vfile, sdk)) {
          weight -= LOCATION;
        }
        else if (ModuleUtilCore.findModuleForFile(containingFile) == null) {
          weight -= LOCATION * 2;
        }

        QualifiedName importPath = QualifiedNameFinder.findShortestImportableQName(file, vfile);
        if (importPath != null) {
          if (!PythonImportUtils.hasImportsFrom(file, importPath)) {
            weight -= LOCATION_ALREADY_IMPORTED;
          }
          int privateApiModifier = (int) StreamEx.of(importPath.getComponents())
            .filter(qName -> qName.startsWith(SINGLE_UNDER))
            .count() * PRIVATE_API;

          weight = weight - privateApiModifier - importPath.getComponentCount();
        }
      }

      if (name.startsWith(DOUBLE_UNDER)) {
        if (name.endsWith(DOUBLE_UNDER)) {
          weight -= UNDERSCORE_IN_NAME * 3; // __foo__ is lowest
        }
        else {
          weight -= UNDERSCORE_IN_NAME * 2; // __foo is lower than normal
        }
      }
      else if (name.startsWith(SINGLE_UNDER)) {
        weight -= UNDERSCORE_IN_NAME;
      }
      return weight;
    }
    return FALLBACK_WEIGHT;
  }
}
