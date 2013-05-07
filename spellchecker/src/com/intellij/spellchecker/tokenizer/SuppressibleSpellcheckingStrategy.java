/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Base class to use to make spellchecking in your language suppressible.
 * Just delegate this to your suppression util code, as you'll do in normal inspection for your language.
 */
public abstract class SuppressibleSpellcheckingStrategy extends SpellcheckingStrategy {
  /**
   * @see com.intellij.codeInspection.CustomSuppressableInspectionTool#isSuppressedFor(com.intellij.psi.PsiElement)
   */
  public abstract boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String name);

  /**
   * @see com.intellij.codeInspection.BatchSuppressableTool#getBatchSuppressActions(com.intellij.psi.PsiElement)
   */
  public abstract SuppressQuickFix[] getSuppressActions(@NotNull PsiElement element, @NotNull String name);
}
