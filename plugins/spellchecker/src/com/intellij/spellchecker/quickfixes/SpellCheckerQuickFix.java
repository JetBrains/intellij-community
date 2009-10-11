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
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.actionSystem.Anchor;
import org.jetbrains.annotations.NotNull;

/**
 * Spell checker quick fix.
 */
public interface SpellCheckerQuickFix extends LocalQuickFix {
  /**
   * Return anchor for actions. Basically return {@link com.intellij.openapi.actionSystem.Anchor#FIRST} for common
   * suggest words, and {@link com.intellij.openapi.actionSystem.Anchor#LAST} for 'Add to ...' actions.
   *
   * @return Action anchor
   */
  @NotNull
  Anchor getPopupActionAnchor();
}
